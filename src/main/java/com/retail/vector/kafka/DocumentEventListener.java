package com.retail.vector.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.retail.vector.dto.DocumentEvent;
import com.retail.vector.service.DocumentProcessingService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DocumentEventListener {

    private static final Logger log = LoggerFactory.getLogger(DocumentEventListener.class);

    private final DocumentProcessingService documentProcessingService;


     @KafkaListener(topics = "${app.kafka.topic.document-events}", groupId = "${spring.kafka.consumer.group-id:document-service-group}")        
    public void handleDocumentEvent(DocumentEvent event) {
        try {
            log.info("Received document event from topic 'document-events': {}", event);
            
            // Validate the event
            if (event == null || event.getDocumentId() == null) {
                log.warn("Invalid document event received: missing required fields");
                return;
            }
            
            // Process based on event type
            String eventType = event.getEventType();
            if (eventType == null) {
                log.warn("Document event has no event type, skipping processing");
                return;
            }
            
            switch (eventType) {
                case "DOCUMENT_CREATED":
                    handleDocumentCreated(event);
                    break;
                case "DOCUMENT_UPDATED":
                    handleDocumentUpdated(event);
                    break;
                case "DOCUMENT_DELETED":
                    handleDocumentDeleted(event);
                    break;
                default:
                    log.warn("Unknown event type: {}", eventType);
            }
            
            log.info("Successfully processed document event: document_id={}, event_type={}", 
                event.getDocumentId(), eventType);
                
        } catch (Exception e) {
            log.error("Error processing document event: {}", event, e);
        }
    }
    
    private void handleDocumentCreated(DocumentEvent event) {
        log.info("Handling DOCUMENT_CREATED event: document_id={}, tenant={}", 
            event.getDocumentId(), event.getTenant());
        // Delegate to processing service (downloads from MinIO, applies XSLT, indexes in Qdrant)
        documentProcessingService.processCreateOrUpdate(event);
    }
    
    private void handleDocumentUpdated(DocumentEvent event) {
        log.info("Handling DOCUMENT_UPDATED event: document_id={}, status={}", 
            event.getDocumentId(), event.getStatus());
        documentProcessingService.processCreateOrUpdate(event);
    }
    
    private void handleDocumentDeleted(DocumentEvent event) {
        log.info("Handling DOCUMENT_DELETED event: document_id={}", event.getDocumentId());
        documentProcessingService.processDelete(event);
    }
}
