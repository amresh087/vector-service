package com.retail.vector.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BrandResponse {
    private Long id;
    private String brand;
    private String nameHi;
    private Boolean isActive;
}
