package com.retail.vector.dto;

import java.util.List;

public class EmbeddingResponse {
    private List<Double> embedding;

    public EmbeddingResponse() {}

    public EmbeddingResponse(List<Double> embedding) {
        this.embedding = embedding;
    }

    public List<Double> getEmbedding() {
        return embedding;
    }

    public void setEmbedding(List<Double> embedding) {
        this.embedding = embedding;
    }
}
