package com.retail.vector.dto;

public class EmbeddingRequest {
    private String prompt;

    public EmbeddingRequest() {}

    public EmbeddingRequest(String prompt) {
        this.prompt = prompt;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }
}
