package com.retail.vector.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductResponse {

    private Long id;
    private String sku;
    private String externalBarcode;
    private String name;
    private String nameHi;
    private String description;
    private String category;
    private String categoryHi;
    private Long brandId;
    private String brandName;
    private String brandNameHi;
    private String unit;
    private Double productSize;
    private Double price;
    private Double discountAmount;
    private String status;
    private String barcode; // Base64 PNG string, optional
    private boolean loose;
    private Double packetSize;
    private String packetUnit;
}
