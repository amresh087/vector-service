package com.retail.vector.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

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
}
