package com.retail.vector.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
class DocumentProcessingServiceTest {

    @Test
    void shouldConvertEmbeddingValuesToFloatArray() {
        float[] vector = DocumentProcessingService.toFloatVector(List.of(0.1, 0.2, 0.3));

        assertArrayEquals(new float[]{0.1f, 0.2f, 0.3f}, vector, 0.0001f);
    }

    @Test
    void shouldReturnEmptyArrayForNullOrEmptyEmbedding() {
        assertArrayEquals(new float[0], DocumentProcessingService.toFloatVector(null));
        assertArrayEquals(new float[0], DocumentProcessingService.toFloatVector(List.of()));
    }

    @Test
    void shouldChunkXmlContentByElementCountAndProduceWellFormedFragments() {
        String segment = "<item>value</item>";
        String content = "<root>" + segment.repeat(200) + "</root>";
        Map<String, io.qdrant.client.grpc.JsonWithInt.Value> payloadMap = new HashMap<>();

        new DocumentProcessingService(null, null, null).embedXsltContentInPayload(content, payloadMap);

        int chunkCount = (int) payloadMap.get("xsltChunkCount").getIntegerValue();
        assertTrue(chunkCount > 1);

        for (int i = 0; i < chunkCount; i++) {
            String chunk = payloadMap.get("xsltChunk_" + i).getStringValue();
            assertTrue(chunk.startsWith("<root>"));
            assertTrue(chunk.endsWith("</root>"));
            assertTrue(chunk.contains("<item>"));
            assertTrue(chunk.contains("</item>"));
        }
    }

    @Test
    void shouldSplitOversizedLeafTextIntoMultipleWellFormedXmlChunks() {
        String longText = "x".repeat(2500);
        String content = "<root><payload>" + longText + "</payload></root>";

        List<String> chunks = new XmlChunker().splitXmlChunks(content);

        assertTrue(chunks.size() > 1);
        for (String chunk : chunks) {
            assertTrue(chunk.startsWith("<root>"));
            assertTrue(chunk.endsWith("</root>"));
            assertTrue(chunk.contains("<payload>"));
            assertTrue(chunk.contains("</payload>"));
        }
    }

    @Test
    void shouldSplitLargeXslTemplateBodyAcrossMultipleChunks() {
        String templateBody = "<xsl:text>line</xsl:text>".repeat(120);
        String content = "<xsl:stylesheet xmlns:xsl='http://w3.org/1999/XSL/Transform' version='1.0'>"
                + "<xsl:output method='text'/>"
                + "<xsl:variable name='quote'>'</xsl:variable>"
                + "<xsl:template match='/'>"
                + templateBody
                + "</xsl:template>"
                + "</xsl:stylesheet>";

        List<String> chunks = new XmlChunker().splitXmlChunks(content);

        assertTrue(chunks.size() > 1, "Expected the large template body to be split across multiple chunks");
        long templateChunkCount = chunks.stream().filter(chunk -> chunk.contains("<xsl:template") && chunk.contains("</xsl:template>")).count();
        assertTrue(templateChunkCount >= 2, "Expected multiple chunks to contain the template body");
    }
}
