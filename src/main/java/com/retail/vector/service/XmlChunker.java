package com.retail.vector.service;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public final class XmlChunker {

    private static final int QDRANT_PAYLOAD_CHUNK_SEGMENT_THRESHOLD = 2; // max segments per XML chunk
    private static final int QDRANT_PAYLOAD_CHUNK_CHAR_THRESHOLD = 800; // max chars per XML chunk

    private XmlChunker() {
    }

    public static List<String> splitXmlChunks(String xml) {
        try {
            DocumentBuilder builder = newSecureDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xml)));

            Element root = document.getDocumentElement();
            return chunkElement(root, QDRANT_PAYLOAD_CHUNK_CHAR_THRESHOLD);
        } catch (Exception e) {
            return splitXmlChunksByRawSegments(xml);
        }
    }

    public static List<String> splitTextChunks(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        for (int offset = 0; offset < text.length(); offset += chunkSize) {
            int end = Math.min(offset + chunkSize, text.length());
            chunks.add(text.substring(offset, end));
        }
        return chunks;
    }

    /**
     * Creates a DocumentBuilder with secure processing enabled and external
     * entity resolution / DOCTYPE declarations disabled to prevent XXE attacks.
     * Centralized here so every call site gets the same hardening.
     */
    private static DocumentBuilder newSecureDocumentBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder();
    }

    private static List<String> chunkElement(Element element, int maxChunkChars) throws Exception {
        String serialized = serializeNode(element);
        if (serialized.length() <= maxChunkChars) {
            return List.of(serialized);
        }

        List<Node> childNodes = new ArrayList<>();
        NodeList nodeList = element.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node child = nodeList.item(i);
            if (child.getNodeType() == Node.TEXT_NODE && child.getTextContent().isBlank()) {
                continue;
            }
            childNodes.add(child);
        }

        boolean hasElementChildren = false;
        for (Node child : childNodes) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                hasElementChildren = true;
                break;
            }
        }

        if (!hasElementChildren && !element.getTextContent().isBlank()) {
            return splitTextContentIntoChunks(element, maxChunkChars);
        }

        List<Node> headerNodes = new ArrayList<>();
        List<Node> bodyNodes = new ArrayList<>();
        boolean seenBody = false;
        for (Node child : childNodes) {
            if (!seenBody && child.getNodeType() == Node.ELEMENT_NODE && isHeaderNode(child)) {
                headerNodes.add(child);
            } else {
                seenBody = true;
                bodyNodes.add(child);
            }
        }

        if (!headerNodes.isEmpty() && !bodyNodes.isEmpty()) {
            int availableSize = Math.max(0, maxChunkChars - serializeNodesLength(headerNodes));
            if (availableSize <= 0) {
                availableSize = maxChunkChars;
            }
            List<List<Node>> groups = groupNodesBySize(bodyNodes, availableSize);
            return splitBodyGroups(element, headerNodes, groups, maxChunkChars);
        }

        if (childNodes.size() > QDRANT_PAYLOAD_CHUNK_SEGMENT_THRESHOLD) {
            List<List<Node>> groups = groupNodesByCount(childNodes, QDRANT_PAYLOAD_CHUNK_SEGMENT_THRESHOLD);
            List<String> chunks = new ArrayList<>();
            for (List<Node> group : groups) {
                chunks.add(buildWrappedXmlChunk(element, group));
            }
            return chunks;
        }

        if (childNodes.size() > 1) {
            List<List<Node>> groups = groupNodesBySize(childNodes, maxChunkChars);
            if (groups.size() > 1) {
                List<String> chunks = new ArrayList<>();
                for (List<Node> group : groups) {
                    chunks.add(buildWrappedXmlChunk(element, group));
                }
                return chunks;
            }
        }

        for (Node child : childNodes) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                List<String> childChunks = chunkElement(childElement, maxChunkChars);
                if (childChunks.size() > 1) {
                    return wrapChildChunks(element, childElement, childChunks);
                }
            }
        }

        return List.of(serialized);
    }

    private static int serializeNodesLength(List<Node> nodes) throws TransformerException {
        int length = 0;
        for (Node node : nodes) {
            length += serializeNode(node).length();
        }
        return length;
    }

    private static boolean isHeaderNode(Node node) {
        if (node.getNodeType() != Node.ELEMENT_NODE) {
            return false;
        }

        String localName = node.getLocalName();
        String nodeName = node.getNodeName();
        String namespace = node.getNamespaceURI();
        boolean isXslNamespace = namespace != null && namespace.contains("w3.org");
        boolean isXslPrefix = nodeName != null && nodeName.startsWith("xsl:");

        if (!isXslNamespace && !isXslPrefix) {
            return false;
        }

        return "output".equals(localName)
                || "variable".equals(localName)
                || "import".equals(localName)
                || "include".equals(localName)
                || "param".equals(localName)
                || "strip-space".equals(localName)
                || "preserve-space".equals(localName)
                || "namespace-alias".equals(localName)
                || "decimal-format".equals(localName)
                || "key".equals(localName)
                || "attribute-set".equals(localName);
    }

    private static List<List<Node>> groupNodesBySize(List<Node> nodes, int maxChunkChars) throws Exception {
        List<List<Node>> groups = new ArrayList<>();
        List<Node> currentGroup = new ArrayList<>();
        int currentSize = 0;

        for (Node node : nodes) {
            String serialized = serializeNode(node);
            int nodeSize = serialized.length();
            if (!currentGroup.isEmpty() && currentSize + nodeSize > maxChunkChars) {
                groups.add(currentGroup);
                currentGroup = new ArrayList<>();
                currentSize = 0;
            }
            currentGroup.add(node);
            currentSize += nodeSize;
        }

        if (!currentGroup.isEmpty()) {
            groups.add(currentGroup);
        }

        return groups;
    }

    private static List<List<Node>> groupNodesByCount(List<Node> nodes, int maxNodeCount) {
        List<List<Node>> groups = new ArrayList<>();
        List<Node> currentGroup = new ArrayList<>();
        int count = 0;

        for (Node node : nodes) {
            if (count >= maxNodeCount) {
                groups.add(currentGroup);
                currentGroup = new ArrayList<>();
                count = 0;
            }
            currentGroup.add(node);
            count++;
        }

        if (!currentGroup.isEmpty()) {
            groups.add(currentGroup);
        }

        return groups;
    }

    private static String wrapChildChunk(Element parent, Element child, String childChunkXml) throws Exception {
        DocumentBuilder builder = newSecureDocumentBuilder();
        Document chunkDocument = builder.newDocument();

        Element parentClone = chunkDocument.createElementNS(parent.getNamespaceURI(), parent.getNodeName());
        copyAttributes(parent, parentClone);
        chunkDocument.appendChild(parentClone);

        for (int i = 0; i < parent.getChildNodes().getLength(); i++) {
            Node sibling = parent.getChildNodes().item(i);
            if (sibling.isSameNode(child)) {
                Document childDoc = builder.parse(new InputSource(new StringReader(childChunkXml)));
                Node chunkRoot = childDoc.getDocumentElement();
                parentClone.appendChild(chunkDocument.importNode(chunkRoot, true));
            } else {
                parentClone.appendChild(chunkDocument.importNode(sibling, true));
            }
        }

        return serializeNode(parentClone);
    }

    private static List<String> wrapChildChunks(Element parent, Element child, List<String> childChunks) throws Exception {
        List<String> wrapped = new ArrayList<>();
        for (String childChunk : childChunks) {
            wrapped.add(wrapChildChunk(parent, child, childChunk));
        }
        return wrapped;
    }

    /**
     * Splits an element's text content into multiple chunks, each wrapped back
     * in a clone of the source element, so that every resulting chunk's
     * serialized length stays within maxChunkChars.
     *
     * The starting chunk size reserves space for the wrapper's actual tag +
     * attribute overhead (measured directly, not guessed), and halves on each
     * retry if a candidate split still doesn't fit. The loop is guaranteed to
     * terminate: it always makes a final decision once chunkSize can no
     * longer be reduced (chunkSize == 1).
     */
    private static List<String> splitTextContentIntoChunks(Element element, int maxChunkChars) throws Exception {
        String text = element.getTextContent();
        if (text == null || text.isBlank()) {
            return List.of(serializeNode(element));
        }

        // Measure the real wrapper overhead (tag name + attributes) instead of guessing.
        int overhead = buildElementWithText(element, "").length();
        int chunkSize = Math.max(1, maxChunkChars - overhead);

        while (true) {
            List<String> candidateChunks = new ArrayList<>();
            boolean tooLarge = false;

            for (String textSegment : splitTextChunks(text, chunkSize)) {
                String wrapped = buildElementWithText(element, textSegment);
                if (wrapped.length() > maxChunkChars) {
                    tooLarge = true;
                    break;
                }
                candidateChunks.add(wrapped);
            }

            if (!tooLarge && candidateChunks.size() > 1) {
                return candidateChunks;
            }

            if (chunkSize <= 1) {
                // Can't shrink further. Return the best available result:
                // a single wrapped chunk if it fit, otherwise the raw fallback.
                if (!tooLarge && candidateChunks.size() == 1) {
                    return candidateChunks;
                }
                return List.of(buildElementWithText(element, text));
            }

            chunkSize = Math.max(1, chunkSize / 2);
        }
    }

    private static String buildElementWithText(Element source, String text) throws Exception {
        DocumentBuilder builder = newSecureDocumentBuilder();
        Document document = builder.newDocument();

        Element element = document.createElementNS(source.getNamespaceURI(), source.getNodeName());
        copyAttributes(source, element);
        element.setTextContent(text);
        document.appendChild(element);

        return serializeNode(element);
    }

    private static List<String> splitBodyGroups(Element root, List<Node> headerNodes, List<List<Node>> groups, int maxChunkChars) throws Exception {
        List<String> chunks = new ArrayList<>();
        for (List<Node> group : groups) {
            if (group.isEmpty()) {
                continue;
            }

            List<Node> combined = new ArrayList<>(headerNodes);
            combined.addAll(group);
            if (group.size() == 1 && group.get(0).getNodeType() == Node.ELEMENT_NODE) {
                Element bodyElement = (Element) group.get(0);
                List<String> childChunks = chunkElement(bodyElement, Math.max(1, maxChunkChars - serializeNodesLength(headerNodes)));
                if (childChunks.size() > 1) {
                    for (String childChunk : childChunks) {
                        chunks.add(buildWrappedXmlChunkWithChild(root, headerNodes, childChunk));
                    }
                    continue;
                }
            }

            chunks.add(buildWrappedXmlChunk(root, combined));
        }
        return chunks;
    }

    private static String buildWrappedXmlChunkWithChild(Element root, List<Node> headerNodes, String childChunkXml) throws Exception {
        DocumentBuilder builder = newSecureDocumentBuilder();
        Document chunkDocument = builder.newDocument();

        Element chunkRoot = chunkDocument.createElementNS(root.getNamespaceURI(), root.getNodeName());
        copyAttributes(root, chunkRoot);
        chunkDocument.appendChild(chunkRoot);

        for (Node headerNode : headerNodes) {
            chunkRoot.appendChild(chunkDocument.importNode(headerNode, true));
        }

        Document childDoc = builder.parse(new InputSource(new StringReader(childChunkXml)));
        Node chunkRootChild = childDoc.getDocumentElement();
        chunkRoot.appendChild(chunkDocument.importNode(chunkRootChild, true));

        return serializeNode(chunkDocument);
    }

    private static String serializeNode(Node node) throws TransformerException {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(node), new StreamResult(writer));
        return writer.toString();
    }

    private static String buildWrappedXmlChunk(Element root, List<Node> childNodes) throws TransformerException {
        try {
            DocumentBuilder builder = newSecureDocumentBuilder();
            Document chunkDocument = builder.newDocument();

            Element chunkRoot = chunkDocument.createElementNS(root.getNamespaceURI(), root.getNodeName());
            copyAttributes(root, chunkRoot);
            chunkDocument.appendChild(chunkRoot);

            for (Node child : childNodes) {
                chunkRoot.appendChild(chunkDocument.importNode(child, true));
            }

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(chunkDocument), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            throw new TransformerException("Failed to build XML chunk", e);
        }
    }

    private static void copyAttributes(Element source, Element target) {
        NamedNodeMap attrs = source.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node attr = attrs.item(i);
            if (attr.getNamespaceURI() != null) {
                target.setAttributeNS(attr.getNamespaceURI(), attr.getNodeName(), attr.getNodeValue());
            } else {
                target.setAttribute(attr.getNodeName(), attr.getNodeValue());
            }
        }
    }

    private static List<String> splitXmlChunksByRawSegments(String xml) {
        List<String> segments = splitXmlSegments(xml);
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentSegmentCount = 0;

        for (String segment : segments) {
            if (segment.isEmpty()) {
                continue;
            }

            if (currentSegmentCount >= QDRANT_PAYLOAD_CHUNK_SEGMENT_THRESHOLD && current.length() > 0) {
                chunks.add(current.toString());
                current.setLength(0);
                currentSegmentCount = 0;
            }

            current.append(segment);
            currentSegmentCount++;
        }

        if (current.length() > 0) {
            chunks.add(current.toString());
        }

        return chunks;
    }

    // Best-effort fallback for XML that failed to parse (e.g. malformed input).
    // Splits naively on '>' — may mis-split content inside comments, CDATA,
    // or attribute values, but only runs when full DOM parsing already failed.
    private static List<String> splitXmlSegments(String xml) {
        List<String> segments = new ArrayList<>();
        int start = 0;

        for (int i = 0; i < xml.length(); i++) {
            if (xml.charAt(i) == '>') {
                segments.add(xml.substring(start, i + 1));
                start = i + 1;
            }
        }

        if (start < xml.length()) {
            segments.add(xml.substring(start));
        }

        return segments;
    }
}