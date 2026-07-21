package com.retail.vector.client;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.retail.vector.dto.EmbeddingRequest;
import com.retail.vector.dto.EmbeddingResponse;

@FeignClient(name = "ai-service", url = "${ai.service.url}")
public interface AiTranformationServiceClient {

        @PostMapping("/ai/embeddings")
        public ResponseEntity<EmbeddingResponse> createEmbedding(@RequestBody EmbeddingRequest request) ;
       
}
