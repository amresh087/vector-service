package com.retail.vector.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "category_brand_mapping",
    uniqueConstraints = @UniqueConstraint(columnNames = {"category_id", "brand_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryBrandMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
