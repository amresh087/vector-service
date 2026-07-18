package com.retail.vector.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.retail.vector.dto.CategoryRequest;
import com.retail.vector.dto.CategoryResponse;
import com.retail.vector.entity.Category;
import com.retail.vector.repository.CategoryRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryResponse create(CategoryRequest request) {

        if (categoryRepository.existsByNameIgnoreCase(request.getCategory())) {
            throw new RuntimeException("Category already exists");
        }

        Category category = Category.builder()
                .name(request.getCategory())
                .nameHi(request.getCategoryHi())
                .isActive(true)
                .build();

        categoryRepository.save(category);

        return new CategoryResponse(category.getId(), category.getName(), category.getNameHi(), category.getIsActive());
    }

    // Get only active categories (for inventory dropdown)
    public List<CategoryResponse> getActive() {
        return categoryRepository.findByIsActiveTrue()
                .stream()
                .map(c -> new CategoryResponse(c.getId(), c.getName(), c.getNameHi(), c.getIsActive()))
                .toList();
    }

    // Get all categories (for admin - including inactive)
    public List<CategoryResponse> getAll() {
        return categoryRepository.findAll()
                .stream()
                .map(c -> new CategoryResponse(c.getId(), c.getName(), c.getNameHi(), c.getIsActive()))
                .toList();
    }

    // Toggle category active status (for admin)
    public CategoryResponse toggleActive(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new RuntimeException("Category not found"));

        category.setIsActive(!category.getIsActive());
        categoryRepository.save(category);

        return new CategoryResponse(category.getId(), category.getName(), category.getNameHi(), category.getIsActive());
    }

    // Update category active status
    public CategoryResponse updateStatus(Long categoryId, Boolean isActive) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new RuntimeException("Category not found"));

        category.setIsActive(isActive);
        categoryRepository.save(category);

        return new CategoryResponse(category.getId(), category.getName(), category.getNameHi(), category.getIsActive());
    }

    // Update category name/properties
    public CategoryResponse update(Long categoryId, CategoryRequest request) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new RuntimeException("Category not found"));

        // Check if new name already exists (excluding current category)
        if (!category.getName().equalsIgnoreCase(request.getCategory()) &&
            categoryRepository.existsByNameIgnoreCase(request.getCategory())) {
            throw new RuntimeException("Category with this name already exists");
        }

        category.setName(request.getCategory());
        if (request.getCategoryHi() != null) {
            category.setNameHi(request.getCategoryHi());
        }
        categoryRepository.save(category);

        return new CategoryResponse(category.getId(), category.getName(), category.getNameHi(), category.getIsActive());
    }
}
