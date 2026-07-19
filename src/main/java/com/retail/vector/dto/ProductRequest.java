package com.retail.vector.dto;

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

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNameHi() { return nameHi; }
    public void setNameHi(String nameHi) { this.nameHi = nameHi; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getCategoryHi() { return categoryHi; }
    public void setCategoryHi(String categoryHi) { this.categoryHi = categoryHi; }
    public Long getBrandId() { return brandId; }
    public void setBrandId(Long brandId) { this.brandId = brandId; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public Double getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(Double discountAmount) { this.discountAmount = discountAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getExternalBarcode() { return externalBarcode; }
    public void setExternalBarcode(String externalBarcode) { this.externalBarcode = externalBarcode; }
    public boolean isLoose() { return loose; }
    public void setLoose(boolean loose) { this.loose = loose; }
    public Double getProductSize() { return productSize; }
    public void setProductSize(Double productSize) { this.productSize = productSize; }
    public Double getPacketSize() { return packetSize; }
    public void setPacketSize(Double packetSize) { this.packetSize = packetSize; }
    public String getPacketUnit() { return packetUnit; }
    public void setPacketUnit(String packetUnit) { this.packetUnit = packetUnit; }

    // Note: `unit` field represents the unit for productSize when sold loose
}
