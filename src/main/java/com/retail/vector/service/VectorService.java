package com.retail.vector.service;

import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.retail.vector.client.AiTranformationServiceClient;
import com.retail.vector.dto.EmbeddingRequest;
import com.retail.vector.dto.EmbeddingResponse;
import com.retail.vector.dto.ProductRequest;
import com.retail.vector.dto.ProductResponse;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.UpdateResult;
import lombok.RequiredArgsConstructor;



@Service
@RequiredArgsConstructor
public class VectorService {


    private final AiTranformationServiceClient aiTranformationServiceClient;

    private final QdrantClient qdrantClient;

    @Value("${qdrant.collection-name}")
    private  String collectionName;

    /**
     * Creates a new product, generates its SKU, checks for uniqueness, generates a barcode, saves it to the database, and triggers embedding generation and Qdrant upsert.
     * @param request
     * @return
     */

    public ProductResponse create(ProductRequest request) {

        ProductResponse response = null;
        genrateEmbeddingAndTriggerQdrantUpsert(response);
     
      return response;
    }

    /**
     * Generates an embedding for the product data and triggers an upsert to the Qdrant collection. It builds the embedding prompt from the product response, creates the embedding using the AI service client, and then constructs the Qdrant payload to save it to the collection.
     * @param response
     */
    public void genrateEmbeddingAndTriggerQdrantUpsert(ProductResponse response) {
         //Get embedding for the product name and save it to the database
       String embeddingPrompt = this.buildDataEmbedding(response);
       ResponseEntity<EmbeddingResponse> embeddingResponse = aiTranformationServiceClient.createEmbedding(EmbeddingRequest.builder().prompt(embeddingPrompt).build());
       List<Double> embedding=embeddingResponse.getBody().getEmbedding();
       this.buildQdrantPayloadAndSaveQdrant(response, embedding);
    }

    /**
     * Builds the Qdrant payload from the product response and embedding, and saves it to the Qdrant collection.
     * @param response
     * @param embedding
     */
    public void buildQdrantPayloadAndSaveQdrant(ProductResponse response, List<Double> embedding) {
        try {
            float[] vectorArray = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                vectorArray[i] = embedding.get(i).floatValue();
            }

           Map<String, io.qdrant.client.grpc.JsonWithInt.Value> payloadMap = new HashMap<>();


            payloadMap.put("productId", value(response.getId()));
            payloadMap.put("sku", value(response.getSku()));
            payloadMap.put("name", value(response.getName()));
            payloadMap.put("nameHi", value(response.getNameHi()));
            payloadMap.put("brandId", value(response.getBrandId()));
            payloadMap.put("brandName", value(response.getBrandName()));
            payloadMap.put("brandNameHi", value(response.getBrandNameHi()));
            payloadMap.put("category", value(response.getCategory()));
            payloadMap.put("categoryHi", value(response.getCategoryHi()));
            payloadMap.put("isLoose", value(response.isLoose()));
            if(response.isLoose()){
                payloadMap.put("productType", value("loose"));
                payloadMap.put("unit", value(response.getUnit()));
                payloadMap.put("productSize", value(response.getProductSize()));
            }else{
                payloadMap.put("productType", value("packet"));
                payloadMap.put("unit", value(response.getPacketUnit()));
                payloadMap.put("productSize", value(response.getPacketSize()));
            }
                     
            payloadMap.put("price", value(response.getPrice()));
            payloadMap.put("status", value(response.getStatus()));

            PointStruct point = PointStruct.newBuilder()
                    .setId(id(response.getId()))
                    .setVectors(vectors(vectorArray))
                    .putAllPayload(payloadMap)
                    .build();

            // Uses the dynamically loaded collection name
            UpdateResult result = qdrantClient.upsertAsync(collectionName, List.of(point)).get();
            System.out.println("Qdrant Upsert Status: " + result.getStatus());
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Failed to upsert payload to Qdrant: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Builds a string representation of the product data for embedding generation.
     * @param response
     * @return
     */
     public String buildDataEmbedding(ProductResponse response){
        
        Double size = null;
        String productType = null;

         if (response.isLoose()) {
             size = response.getProductSize();
             productType= " loose";
         } else {
             size = response.getPacketSize();
             productType= " packet";
         }

         String embeddingText = String.format(
                 "Brand %s. Product %s. Hindi product %s. Category %s. Hindi category %s. Size %s %s. isLoose: %s. productType: %s.",
                 response.getBrandName(), response.getName(), response.getNameHi(), response.getCategory(),
                 response.getCategoryHi(),
                 size,
                 response.getUnit(),
                 response.isLoose(),
                 productType);
         return embeddingText;
     }




    



}
