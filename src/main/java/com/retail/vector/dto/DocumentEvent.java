package com.retail.vector.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DocumentEvent {

    @JsonProperty("document_id")
    private String documentId;

    @JsonProperty("document_name")
    private String documentName;

    @JsonProperty("document_type")
    private String documentType;

    @JsonProperty("tenant")
    private String tenant;

    @JsonProperty("transaction_type_code")
    private String transactionTypeCode;

    @JsonProperty("mapping_type")
    private String mappingType;

    @JsonProperty("status")
    private String status;

    @JsonProperty("object_name")
    private String objectName;

    @JsonProperty("event_type")
    private String eventType;

    @JsonProperty("timestamp")
    private String timestamp;

    public DocumentEvent() {}

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getDocumentName() { return documentName; }
    public void setDocumentName(String documentName) { this.documentName = documentName; }

    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }

    public String getTenant() { return tenant; }
    public void setTenant(String tenant) { this.tenant = tenant; }

    public String getTransactionTypeCode() { return transactionTypeCode; }
    public void setTransactionTypeCode(String transactionTypeCode) { this.transactionTypeCode = transactionTypeCode; }

    public String getMappingType() { return mappingType; }
    public void setMappingType(String mappingType) { this.mappingType = mappingType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getObjectName() { return objectName; }
    public void setObjectName(String objectName) { this.objectName = objectName; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

}
