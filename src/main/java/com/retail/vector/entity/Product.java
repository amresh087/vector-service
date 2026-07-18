package com.retail.vector.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "product",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = "sku"),
        @UniqueConstraint(columnNames = "external_barcode")
    }
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(name = "external_barcode", unique = true)
    private String externalBarcode;

    @Column(nullable = false)
    private String name;

     @Column(nullable = false)
    private String nameHi;

    private String description;

    private String category;

    private String categoryHi;

    @Column(name = "brand_id")
    private Long brandId;

    private String unit;   // PCS / KG / LTR

    @Column(name = "is_loose", nullable = false)
    private boolean loose; // true = sold loose/bulk, false = packaged unit

    @Column(name = "packet_size")
    private Double packetSize; // numeric size of the packet (e.g., 5.0, 1.0, 500.0)

    @Column(name = "packet_unit")
    private String packetUnit; // unit of the packet contents (KG, G, LTR, ML)

    @Column(name = "product_size")
    private Double productSize; // numeric size when sold loose (e.g., 0.1 for 100g)

    private Double price;

    @Column(name = "discount_amount")
    private Double discountAmount; // Discount in rupees

    @Column(nullable = false)
    private String status; // ACTIVE / INACTIVE

    @Lob
    @Column(name = "barcode", columnDefinition = "LONGBLOB")
    private byte[] barcode;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Transient
    public String getContainedUnit() {
        return packetUnit != null ? packetUnit : unit;
    }
}
