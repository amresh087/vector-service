package com.retail.vector.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ProductRequest {
   
    private String name;
    private String nameHi;
    private String description;
    private String category;
    private String categoryHi;
    private Long brandId;
    private String unit;
    private Double price;
    private Double discountAmount;
    private String status;
    private String externalBarcode; // External barcode number (can be scanned)
    private boolean loose; // true = sold loose/bulk, false = packaged unit
    private Double productSize; // numeric size when sold loose (e.g., per 100g)
    private Double packetSize; // numeric size of the packet
    private String packetUnit; // unit of the packet contents (KG, G, LTR, ML)

    // Note: `unit` field represents the unit for productSize when sold loose
}
