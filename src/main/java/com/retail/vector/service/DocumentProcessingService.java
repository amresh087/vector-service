package com.retail.vector.service;

import com.retail.vector.client.AiTranformationServiceClient;
import com.retail.vector.dto.DocumentEvent;
import com.retail.vector.dto.EmbeddingRequest;
import com.retail.vector.dto.EmbeddingResponse;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
// removed unused MinIO put import; MinIO file storage for XSLT mappings removed
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.PointStruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;

@Service
@RequiredArgsConstructor
public class DocumentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingService.class);

    private final MinioClient minioClient;
    private final AiTranformationServiceClient aiClient;
    private final QdrantClient qdrantClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${qdrant.collection-name}")
    private String collectionName;

    public void processCreateOrUpdate(DocumentEvent event) {
        try {
            if (event.getObjectName() == null) {
                log.warn("Event has no object name, skipping download: {}", event);
                return;
            }

            // Download original object
            try (InputStream objStream = minioClient.getObject(
                    GetObjectArgs.builder().bucket(bucketName).object(event.getObjectName()).build())) {
                String original = new String(objStream.readAllBytes(), StandardCharsets.UTF_8);

                // Generate embedding using AI transformation service from the original content
                // only
                ResponseEntity<EmbeddingResponse> resp = aiClient.createEmbedding(new EmbeddingRequest(original));
                List<Double> embedding = resp.getBody() != null ? resp.getBody().getEmbedding() : null;

                float[] vectorArray = toFloatVector(embedding);
                if (vectorArray.length == 0) {
                    log.warn("Embedding generation returned empty for document {}", event.getDocumentId());
                    return;
                }

                Map<String, io.qdrant.client.grpc.JsonWithInt.Value> payloadMap = new HashMap<>();
                payloadMap.put("documentId", value(event.getDocumentId()));
                payloadMap.put("documentName", value(event.getDocumentName()));
                payloadMap.put("tenant", value(event.getTenant()));
                payloadMap.put("transactionTypeCode", value(event.getTransactionTypeCode()));
                payloadMap.put("mappingType", value(event.getMappingType()));
                payloadMap.put("objectName", value(event.getObjectName()));
                payloadMap.put("status", value(event.getStatus()));

                // Qdrant PointIdFactory expects a numeric id; derive a stable long from the
                // documentId string
                long numericId = UUID.nameUUIDFromBytes(event.getDocumentId().getBytes(StandardCharsets.UTF_8))
                        .getMostSignificantBits() & Long.MAX_VALUE;

                PointStruct point = PointStruct.newBuilder()
                        .setId(id(numericId))
                        .setVectors(vectors(vectorArray))
                        .putAllPayload(payloadMap)
                        .build();

                qdrantClient.upsertAsync(collectionName, List.of(point)).get();
                log.info("Indexed document {} into Qdrant collection {}", event.getDocumentId(), collectionName);
            }
        } catch (ExecutionException e) {
            log.error(
                    "Qdrant gRPC error while processing document {}: {} (ensure Qdrant is running on configured host/port)",
                    event.getDocumentId(), e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
        } catch (InterruptedException e) {
            log.warn("Processing of document {} was interrupted while waiting on a downstream store",
                    event.getDocumentId(), e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Error processing document event {}", event, e);
        }
    }

    public void processDelete(DocumentEvent event) {
        try {
            long numericIdDelete = UUID.nameUUIDFromBytes(event.getDocumentId().getBytes(StandardCharsets.UTF_8)).getMostSignificantBits() & Long.MAX_VALUE;

            Points.PointId qdrantPointId = Points.PointId.newBuilder()
                    .setNum(numericIdDelete)
                    .build();

            Points.PointsIdsList pointsIdsList = Points.PointsIdsList.newBuilder()
                    .addIds(qdrantPointId)
                    .build();

            Points.PointsSelector pointsSelector = Points.PointsSelector.newBuilder()
                    .setPoints(pointsIdsList)
                    .build();

            Points.DeletePoints deletePoints = Points.DeletePoints.newBuilder()
                    .setCollectionName(collectionName)
                    .setPoints(pointsSelector)
                    .build();

            qdrantClient.deleteAsync(deletePoints).get();
            log.info("Deleted document {} from Qdrant collection {}", event.getDocumentId(), collectionName);

        } catch (InterruptedException e) {
            log.warn("Delete update for document {} was interrupted while waiting on Qdrant", event.getDocumentId(), e);
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            log.error("Failed to delete document {} in Qdrant", event.getDocumentId(), e);
        }
    }

    static float[] toFloatVector(List<Double> embedding) {
        if (embedding == null || embedding.isEmpty()) {
            return new float[0];
        }

        float[] vectorArray = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            vectorArray[i] = embedding.get(i).floatValue();
        }
        return vectorArray;
    }

}
