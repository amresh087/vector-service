package com.retail.vector.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.retail.vector.entity.Brand;
import com.retail.vector.entity.Category;
import com.retail.vector.entity.CategoryBrandMapping;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryBrandMappingRepository extends JpaRepository<CategoryBrandMapping, Long> {

    // Get all brands mapped to a category
    List<CategoryBrandMapping> findByCategory(Category category);

    // Get all brands by category ID
    List<CategoryBrandMapping> findByCategory_Id(Long categoryId);

    // Get all categories mapped to a brand
    List<CategoryBrandMapping> findByBrand(Brand brand);

    // Get all categories by brand ID
    List<CategoryBrandMapping> findByBrand_Id(Long brandId);

    // Check if a mapping exists
    Optional<CategoryBrandMapping> findByCategory_IdAndBrand_Id(Long categoryId, Long brandId);

    // Delete mapping
    void deleteByCategory_IdAndBrand_Id(Long categoryId, Long brandId);

    // Find active mappings
    List<CategoryBrandMapping> findByCategory_IdAndIsActiveTrue(Long categoryId);
}
