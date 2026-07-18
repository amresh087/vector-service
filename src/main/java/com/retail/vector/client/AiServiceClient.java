package com.retail.vector.client;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.retail.vector.dto.EmbeddingRequest;
import com.retail.vector.dto.EmbeddingResponse;

@FeignClient(name = "ai-service", url = "http://localhost:2050")
public interface AiServiceClient {

        @PostMapping("/ai/embeddings")
        public ResponseEntity<EmbeddingResponse> createEmbedding(@RequestBody EmbeddingRequest request) ;
       
       
    

}
