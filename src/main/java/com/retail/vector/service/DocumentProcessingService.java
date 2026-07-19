package com.retail.vector.service;

import com.retail.vector.client.AiTranformationServiceClient;
import com.retail.vector.dto.DocumentEvent;
import com.retail.vector.dto.EmbeddingRequest;
import com.retail.vector.dto.EmbeddingResponse;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points.PointStruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
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

                // Try to find a matching XSLT mapping file in the same bucket
                String xslt = findXsltFor(event);
                String idocContent = original;
                if (xslt != null) {
                    try {
                        idocContent = applyXslt(original, xslt);
                    } catch (Exception e) {
                        log.warn("Failed to apply XSLT for document {}, continuing with original content", event.getDocumentId(), e);
                        idocContent = original;
                    }
                }

                // Save generated idoc back to MinIO
                byte[] idocBytes = idocContent.getBytes(StandardCharsets.UTF_8);
                String idocObjectName = event.getDocumentId() + "-idoc-content.xml";
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(idocObjectName)
                        .stream(new ByteArrayInputStream(idocBytes), idocBytes.length, -1)
                        .contentType("application/xml")
                        .build());

                // Generate embedding using AI transformation service
                ResponseEntity<EmbeddingResponse> resp = aiClient.createEmbedding(new EmbeddingRequest(idocContent));
                List<Double> embedding = resp.getBody() != null ? resp.getBody().getEmbedding() : null;

                if (embedding == null || embedding.isEmpty()) {
                    log.warn("Embedding generation returned empty for document {}", event.getDocumentId());
                    return;
                }

                float[] vectorArray = new float[embedding.size()];
                for (int i = 0; i < embedding.size(); i++) {
                    vectorArray[i] = embedding.get(i).floatValue();
                }

                Map<String, io.qdrant.client.grpc.JsonWithInt.Value> payloadMap = new HashMap<>();
                payloadMap.put("documentId", value(event.getDocumentId()));
                payloadMap.put("documentName", value(event.getDocumentName()));
                payloadMap.put("tenant", value(event.getTenant()));
                payloadMap.put("transactionTypeCode", value(event.getTransactionTypeCode()));
                payloadMap.put("objectName", value(event.getObjectName()));
                payloadMap.put("status", value(event.getStatus()));

                // Qdrant PointIdFactory expects a numeric id; derive a stable long from the documentId string
                long numericId = UUID.nameUUIDFromBytes(event.getDocumentId().getBytes(StandardCharsets.UTF_8)).getMostSignificantBits() & Long.MAX_VALUE;

                PointStruct point = PointStruct.newBuilder()
                    .setId(id(numericId))
                    .setVectors(vectors(vectorArray))
                    .putAllPayload(payloadMap)
                    .build();

                qdrantClient.upsertAsync(collectionName, List.of(point)).get();
                log.info("Indexed document {} into Qdrant collection {}", event.getDocumentId(), collectionName);
            }

        } catch (ExecutionException e) {
            log.error("Qdrant gRPC error while processing document {}: {} (ensure Qdrant is running on configured host/port)", 
                event.getDocumentId(), e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
        } catch (InterruptedException e) {
            log.error("Interrupted while processing document {}", event.getDocumentId(), e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Error processing document event {}", event, e);
        }
    }

    public void processDelete(DocumentEvent event) {
        try {
            // Mark document as DELETED by upserting a payload with status=DELETED using numeric id
            long numericIdDelete = UUID.nameUUIDFromBytes(event.getDocumentId().getBytes(StandardCharsets.UTF_8)).getMostSignificantBits() & Long.MAX_VALUE;

            Map<String, io.qdrant.client.grpc.JsonWithInt.Value> payloadMap = new HashMap<>();
            payloadMap.put("documentId", value(event.getDocumentId()));
            payloadMap.put("status", value("DELETED"));

            PointStruct point = PointStruct.newBuilder()
                    .setId(id(numericIdDelete))
                    .putAllPayload(payloadMap)
                    .build();

            qdrantClient.upsertAsync(collectionName, List.of(point)).get();
            log.info("Marked document {} as DELETED in Qdrant collection {}", event.getDocumentId(), collectionName);

        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to delete/mark document {} in Qdrant", event.getDocumentId(), e);
            Thread.currentThread().interrupt();
        }
    }

    private String findXsltFor(DocumentEvent event) {
        try {
            if (event.getObjectName() != null) {

                try {
                    InputStream xsltIs = minioClient.getObject(GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(event.getObjectName())
                            .build());
                    log.debug("Found XSLT mapping at: {}", event.getObjectName());
                    return new String(xsltIs.readAllBytes(), StandardCharsets.UTF_8);
                } catch (Exception e) {
                    log.debug("No XSLT mapping found at: {}", event.getObjectName());
                }
            }

            log.debug("No XSLT mapping found for document {}", event.getDocumentId());
        } catch (Exception e) {
            log.debug("Error searching for XSLT mapping for document {}", event.getDocumentId(), e);
        }
        return null;
    }

    private String applyXslt(String input, String xslt) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        Source xsltSource = new StreamSource(new StringReader(xslt));
        Transformer transformer = factory.newTransformer(xsltSource);

        Source text = new StreamSource(new StringReader(input));
        StringWriter writer = new StringWriter();
        transformer.transform(text, new StreamResult(writer));
        return writer.toString();
    }
}
