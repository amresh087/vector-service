package com.retail.vector.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CategoryResponse {
    private Long id;
    private String category;
    private String categoryHi;
    private Boolean isActive;
}
