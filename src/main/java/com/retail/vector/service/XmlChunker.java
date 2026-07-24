package com.retail.vector.service;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
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

public class XmlChunker {

    private final int segmentThreshold;
    private final int charThreshold;

    public XmlChunker() {
        this(5, 1800);
    }

    public XmlChunker(@Value("${qdrant.payload.chunk-segment-threshold}") int segmentThreshold,
                     @Value("${qdrant.payload.chunk-char-threshold}") int charThreshold) {
        this.segmentThreshold = segmentThreshold;
        this.charThreshold = charThreshold;
    }

    /**
     * Default method splitting into 2 chunks.
     */
    public List<String> splitXmlChunks(String xml) {
        return splitXmlChunks(xml, 2);
    }

    /**
     * Unified method where targetChunks decides how many chunks to create
     * while guaranteeing zero tags, attributes, or child nodes are missed or dropped.
     */
    public List<String> splitXmlChunks(String xml, int targetChunks) {
        try {
            DocumentBuilder builder = newSecureDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xml)));

            Element root = document.getDocumentElement();
            return chunkElementSafely(root, targetChunks);
        } catch (Exception e) {
            return splitRawXmlSafely(xml, targetChunks);
        }
    }

    public List<String> splitTextChunks(String text, int chunkSize) {
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

    private List<String> chunkElementSafely(Element element, int targetChunks) throws Exception {
        List<Node> allChildNodes = new ArrayList<>();
        NodeList nodeList = element.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            allChildNodes.add(nodeList.item(i));
        }

        if (allChildNodes.isEmpty() || targetChunks <= 1) {
            return List.of(serializeNode(element));
        }

        List<List<Node>> partitionedGroups = partitionNodesEvenly(allChildNodes, targetChunks);
        List<String> chunks = new ArrayList<>();

        for (List<Node> group : partitionedGroups) {
            if (group.isEmpty()) {
                continue;
            }
            chunks.add(buildWrappedXmlChunk(element, group));
        }

        return chunks;
    }

    private List<List<Node>> partitionNodesEvenly(List<Node> nodes, int targetChunks) {
        List<List<Node>> groups = new ArrayList<>();
        int totalNodes = nodes.size();
        
        int actualChunks = Math.min(targetChunks, totalNodes);
        int baseSize = totalNodes / actualChunks;
        int remainder = totalNodes % actualChunks;

        int currentIndex = 0;
        for (int i = 0; i < actualChunks; i++) {
            int currentChunkSize = baseSize + (i < remainder ? 1 : 0);
            List<Node> group = new ArrayList<>();
            for (int j = 0; j < currentChunkSize; j++) {
                group.add(nodes.get(currentIndex++));
            }
            groups.add(group);
        }

        return groups;
    }

    private String serializeNode(Node node) throws TransformerException {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(node), new StreamResult(writer));
        return writer.toString();
    }

    private String buildWrappedXmlChunk(Element root, List<Node> childNodes) throws TransformerException {
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

    private void copyAttributes(Element source, Element target) {
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

    private List<String> splitRawXmlSafely(String xml, int targetChunks) {
        List<String> segments = splitXmlSegments(xml);
        if (segments.size() <= targetChunks) {
            return List.of(xml);
        }

        List<List<String>> partitionedSegments = new ArrayList<>();
        int totalSegments = segments.size();
        int baseSize = totalSegments / targetChunks;
        int remainder = totalSegments % targetChunks;

        int currentIndex = 0;
        for (int i = 0; i < targetChunks; i++) {
            int currentSize = baseSize + (i < remainder ? 1 : 0);
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < currentSize; j++) {
                sb.append(segments.get(currentIndex++));
            }
            partitionedSegments.add(List.of(sb.toString()));
        }

        List<String> result = new ArrayList<>();
        for (List<String> group : partitionedSegments) {
            result.add(String.join("", group));
        }
        return result;
    }

    private List<String> splitXmlSegments(String xml) {
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