package com.retail.vector.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
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

    @JsonProperty("status")
    private String status;

    @JsonProperty("object_name")
    private String objectName;

    @JsonProperty("event_type")
    private String eventType;

    @JsonProperty("timestamp")
    private String timestamp;

}
