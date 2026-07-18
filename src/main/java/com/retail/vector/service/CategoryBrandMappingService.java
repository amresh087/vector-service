package com.retail.vector.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.retail.vector.entity.Brand;
import com.retail.vector.entity.Category;
import com.retail.vector.entity.CategoryBrandMapping;
import com.retail.vector.repository.BrandRepository;
import com.retail.vector.repository.CategoryBrandMappingRepository;
import com.retail.vector.repository.CategoryRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryBrandMappingService {

    private final CategoryBrandMappingRepository mappingRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;

    /**
     * Get all brands mapped to a category
     */
    public List<Brand> getBrandsByCategory(Long categoryId) {
        List<CategoryBrandMapping> mappings = mappingRepository.findByCategory_IdAndIsActiveTrue(categoryId);
        return mappings.stream()
                .map(CategoryBrandMapping::getBrand)
                .collect(Collectors.toList());
    }

    /**
     * Get all categories mapped to a brand
     */
    public List<Category> getCategoriesByBrand(Long brandId) {
        List<CategoryBrandMapping> mappings = mappingRepository.findByBrand_Id(brandId);
        return mappings.stream()
                .map(CategoryBrandMapping::getCategory)
                .collect(Collectors.toList());
    }

    /**
     * Create a new category-brand mapping
     */
    @Transactional
    public CategoryBrandMapping mapBrandToCategory(Long categoryId, Long brandId) {
        // Check if mapping already exists
        if (mappingRepository.findByCategory_IdAndBrand_Id(categoryId, brandId).isPresent()) {
            throw new IllegalArgumentException("Brand is already mapped to this category");
        }

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new RuntimeException("Category not found with ID: " + categoryId));

        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new RuntimeException("Brand not found with ID: " + brandId));

        CategoryBrandMapping mapping = CategoryBrandMapping.builder()
                .category(category)
                .brand(brand)
                .isActive(true)
                .build();

        return mappingRepository.save(mapping);
    }

    /**
     * Remove a category-brand mapping
     */
    @Transactional
    public void unmapBrandFromCategory(Long categoryId, Long brandId) {
        CategoryBrandMapping mapping = mappingRepository.findByCategory_IdAndBrand_Id(categoryId, brandId)
                .orElseThrow(() -> new RuntimeException("Mapping not found"));

        mappingRepository.delete(mapping);
    }

    /**
     * Check if a brand is mapped to a category
     */
    public boolean isBrandMappedToCategory(Long categoryId, Long brandId) {
        return mappingRepository.findByCategory_IdAndBrand_Id(categoryId, brandId).isPresent();
    }

    /**
     * Get all mappings for a category
     */
    public List<CategoryBrandMapping> getAllMappingsForCategory(Long categoryId) {
        return mappingRepository.findByCategory_Id(categoryId);
    }
}
