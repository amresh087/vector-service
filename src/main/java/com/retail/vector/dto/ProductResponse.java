package com.retail.vector.dto;

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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getExternalBarcode() { return externalBarcode; }
    public void setExternalBarcode(String externalBarcode) { this.externalBarcode = externalBarcode; }
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
    public String getBrandName() { return brandName; }
    public void setBrandName(String brandName) { this.brandName = brandName; }
    public String getBrandNameHi() { return brandNameHi; }
    public void setBrandNameHi(String brandNameHi) { this.brandNameHi = brandNameHi; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public Double getProductSize() { return productSize; }
    public void setProductSize(Double productSize) { this.productSize = productSize; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public Double getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(Double discountAmount) { this.discountAmount = discountAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public boolean isLoose() { return loose; }
    public void setLoose(boolean loose) { this.loose = loose; }
    public Double getPacketSize() { return packetSize; }
    public void setPacketSize(Double packetSize) { this.packetSize = packetSize; }
    public String getPacketUnit() { return packetUnit; }
    public void setPacketUnit(String packetUnit) { this.packetUnit = packetUnit; }
}
