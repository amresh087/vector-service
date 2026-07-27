package com.retail.vector.service;

import com.retail.vector.client.AiTranformationServiceClient;
import com.retail.vector.dto.DocumentEvent;
import com.retail.vector.dto.EmbeddingRequest;
import com.retail.vector.dto.EmbeddingResponse;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.PointStruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;

@Service
public class DocumentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingService.class);

    private final MinioClient minioClient;
    private final AiTranformationServiceClient aiClient;
    private final QdrantClient qdrantClient;
    private final XmlChunker xmlChunker;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${qdrant.collection-name}")
    private String collectionName;

    @Value("${xml.chunk.size}")
    private int xmlChunkSize;

    @Value("${xml.chunk.mapping-types}")
    private String xmlChunkMappingTypes;

    @Value("${xml.chunk.text-chunk-size}")
    private int textChunkSize;

    @Value("${embedding.retry.attempts}")
    private int embeddingRetryAttempts;

    @Value("${embedding.retry.initial-delay-ms}")
    private long embeddingRetryInitialDelayMs;

    @Value("${embedding.retry.backoff-multiplier}")
    private double embeddingRetryBackoffMultiplier;

    @Autowired
    public DocumentProcessingService(MinioClient minioClient, AiTranformationServiceClient aiClient,
                                     QdrantClient qdrantClient) {
        this(minioClient, aiClient, qdrantClient, new XmlChunker());
    }

    public DocumentProcessingService(MinioClient minioClient, AiTranformationServiceClient aiClient,
                                     QdrantClient qdrantClient, XmlChunker xmlChunker) {
        this.minioClient = minioClient;
        this.aiClient = aiClient;
        this.qdrantClient = qdrantClient;
        this.xmlChunker = xmlChunker;
    }

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
                boolean shouldChunkXml = Arrays.stream(xmlChunkMappingTypes.split(","))
                    .map(String::trim)
                    .anyMatch(t -> t.equalsIgnoreCase(event.getMappingType()));

                if (shouldChunkXml) {
                    List<String> chunkTexts = xmlChunker.splitXmlChunks(original, xmlChunkSize);
                    List<PointStruct> points = new ArrayList<>();

                    for (int chunkIndex = 0; chunkIndex < chunkTexts.size(); chunkIndex++) {
                        String chunkText = chunkTexts.get(chunkIndex);
                        List<Double> embedding = embedWithRetry(chunkText, event, chunkIndex);
                        if (!isValidEmbedding(embedding)) {
                            log.warn("Skipping chunk {} for document {} because embedding was empty", chunkIndex, event.getDocumentId());
                            continue;
                        }

                        float[] vectorArray = toFloatVector(embedding);
                        String status = event.getStatus();

                        Map<String, io.qdrant.client.grpc.JsonWithInt.Value> payloadMap = new HashMap<>();
                        payloadMap.put("documentId", value(event.getDocumentId()));
                        payloadMap.put("documentName", value(event.getDocumentName()));
                        payloadMap.put("tenant", value(event.getTenant()));
                        payloadMap.put("transactionTypeCode", value(event.getTransactionTypeCode()));
                        payloadMap.put("mappingType", value(event.getMappingType()));
                        payloadMap.put("objectName", value(event.getObjectName()));
                        payloadMap.put("status", value(status));
                        payloadMap.put("xsltChunkIndex", value(chunkIndex));
                        payloadMap.put("xsltChunkCount", value(chunkTexts.size()));
                        payloadMap.put("xsltChunkText", value(chunkText));

                        long numericId = UUID.nameUUIDFromBytes(
                                (event.getDocumentId() + "-" + chunkIndex).getBytes(StandardCharsets.UTF_8))
                                .getMostSignificantBits() & Long.MAX_VALUE;

                        PointStruct point = PointStruct.newBuilder()
                                .setId(id(numericId))
                                .setVectors(vectors(vectorArray))
                                .putAllPayload(payloadMap)
                                .build();
                        points.add(point);
                    }

                    if (points.isEmpty()) {
                        log.warn("No valid chunks for document {} were indexed because all embeddings were empty", event.getDocumentId());
                    } else {
                        qdrantClient.upsertAsync(collectionName, points).get();
                        if (points.size() != chunkTexts.size()) {
                            log.warn("Reconciled {} points for {} expected chunks for document {}", points.size(), chunkTexts.size(), event.getDocumentId());
                        }
                        log.info("Indexed document {} into Qdrant collection {} with {} chunks", event.getDocumentId(), collectionName, points.size());
                    }
                } else {
                    List<Double> embedding = embedWithRetry(original, event, -1);
                    if (!isValidEmbedding(embedding)) {
                        log.warn("Skipping indexing for document {} because embedding generation returned empty", event.getDocumentId());
                        return;
                    }

                    float[] vectorArray = toFloatVector(embedding);
                    String status = event.getStatus();

                    Map<String, io.qdrant.client.grpc.JsonWithInt.Value> payloadMap = new HashMap<>();
                    payloadMap.put("documentId", value(event.getDocumentId()));
                    payloadMap.put("documentName", value(event.getDocumentName()));
                    payloadMap.put("tenant", value(event.getTenant()));
                    payloadMap.put("transactionTypeCode", value(event.getTransactionTypeCode()));
                    payloadMap.put("mappingType", value(event.getMappingType()));
                    payloadMap.put("objectName", value(event.getObjectName()));
                    payloadMap.put("status", value(status));

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

    void embedXsltContentInPayload(String payloadText, Map<String, io.qdrant.client.grpc.JsonWithInt.Value> payloadMap) {
        if (payloadText == null || payloadText.isBlank()) {
            return;
        }

        boolean isXml = payloadText.trim().startsWith("<");
        List<String> chunks = isXml
            ? xmlChunker.splitXmlChunks(payloadText)
            : xmlChunker.splitTextChunks(payloadText, textChunkSize);

        for (int chunkIndex = 0; chunkIndex < chunks.size(); chunkIndex++) {
            payloadMap.put("xsltChunk_" + chunkIndex, value(chunks.get(chunkIndex)));
        }

        payloadMap.put("xsltChunkCount", value(chunks.size()));
    }

    public void processDelete(DocumentEvent event) {
        try {
            Points.Filter filter = Points.Filter.newBuilder()
                    .addMust(
                            Points.Condition.newBuilder()
                                    .setField(
                                            Points.FieldCondition.newBuilder()
                                                    .setKey("documentId")
                                                    .setMatch(
                                                            Points.Match.newBuilder()
                                                                    .setKeyword(event.getDocumentId())
                                                                    .build())
                                                    .build())
                                    .build())
                    .build();

            Points.DeletePoints deletePoints = Points.DeletePoints.newBuilder()
                    .setCollectionName(collectionName)
                    .setPoints(
                            Points.PointsSelector.newBuilder()
                                    .setFilter(filter)
                                    .build())
                    .setWait(true)
                    .build();

            qdrantClient.deleteAsync(deletePoints).get();

            log.info("Deleted all points for documentId={}", event.getDocumentId());

        } catch (Exception e) {
            log.error("Failed to delete document {}", event.getDocumentId(), e);
        }
    }

    private List<Double> embedWithRetry(String chunkText, DocumentEvent event, int chunkIndex) {
        long delay = embeddingRetryInitialDelayMs;
        for (int attempt = 1; attempt <= Math.max(1, embeddingRetryAttempts); attempt++) {
            try {

                EmbeddingRequest request = EmbeddingRequest.builder()
                        .chunkText(chunkText)
                        .build();

                ResponseEntity<EmbeddingResponse> response = aiClient.createEmbedding(request);
                List<Double> embedding = response.getBody() != null ? response.getBody().getEmbedding() : null;
                if (embedding != null && !embedding.isEmpty()) {
                    return embedding;
                }
                log.warn("Embedding attempt {} returned empty embedding for document {} chunk {}", attempt, event.getDocumentId(), chunkIndex);
            } catch (Exception e) {
                log.warn("Embedding attempt {} threw exception for document {} chunk {}: {}", attempt, event.getDocumentId(), chunkIndex, e.getMessage());
            }

            if (attempt < embeddingRetryAttempts) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Embedding retry sleep interrupted for document {} chunk {}", event.getDocumentId(), chunkIndex);
                    break;
                }
                delay = (long) (delay * Math.max(1.0, embeddingRetryBackoffMultiplier));
            }
        }
        return List.of();
    }

    static boolean isValidEmbedding(List<Double> embedding) {
        return embedding != null && !embedding.isEmpty();
    }

    static float[] toFloatVector(List<Double> embedding) {
        if (!isValidEmbedding(embedding)) {
            return new float[0];
        }

        float[] vectorArray = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            vectorArray[i] = embedding.get(i).floatValue();
        }
        return vectorArray;
    }

}
