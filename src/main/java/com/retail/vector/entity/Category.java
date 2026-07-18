package com.retail.vector.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "product_category",
    uniqueConstraints = @UniqueConstraint(columnNames = "name")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false, length = 100)
    private String nameHi;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
