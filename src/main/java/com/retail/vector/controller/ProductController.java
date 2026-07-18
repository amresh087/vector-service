package com.retail.vector.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.io.ByteArrayOutputStream;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.retail.vector.dto.*;
import com.retail.vector.entity.Brand;
import com.retail.vector.service.CategoryBrandMappingService;
import com.retail.vector.service.ProductService;
import com.google.zxing.client.j2se.MatrixToImageWriter;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final CategoryBrandMappingService categoryBrandMappingService;

    /**
     * Create a new product.
     */
    @PostMapping
    public ProductResponse create(@RequestBody ProductRequest request) {

        return productService.create(request);
    }

    /**
     * Create multiple new products.
     */
    @PostMapping("/bulk")
    public String createAll(@RequestBody List<ProductRequest> requests) {
        for (ProductRequest request : requests) {
            productService.create(request);
        }
        return "done";
    }

    /**
     * Get all products.
     */
    @GetMapping
    public List<ProductResponse> getAll() {
        return productService.getAll();
    }

    /**
     * Get a product by ID.
     */
    @GetMapping("/{id}")
    public ProductResponse getById(@PathVariable Long id) {
        return productService.getById(id);
    }

    /**
     * Get products by category.
     */
    @GetMapping("/search/category")
    public List<String> getByCategory(@RequestParam String category) {
        return productService.getByCategory(category);
    }

    /**
     * Get products by brand ID.
     */
    @GetMapping("/search/brand")
    public List<String> getByBrand(@RequestParam Long brandId) {
        return productService.getByBrand(brandId);
    }

    /**
     * Get products by name.
     */
    @GetMapping("/search/name")
    public List<ProductResponse> getByName(@RequestParam String name, @RequestParam("language") String language) {
        return productService.getByName(name, language);
    }

    @GetMapping("/search/sku")
    public ProductResponse getBySku(@RequestParam String sku) {
        return productService.getAllSku(sku);
    }

    /**
     * Search product by barcode (either SKU or external barcode).
     * This endpoint works for both SKU scanning and external barcode scanning.
     */
    @GetMapping("/search/barcode")
    public ProductResponse searchByBarcode(@RequestParam String barcode) {
        return productService.searchByBarcode(barcode);
    }

    /**     * Filter products for customer shopping view by category, brand, and search term.
     * All parameters are optional. Returns only ACTIVE products.
     */
    @GetMapping("/filter")
    public List<ProductResponse> filterProducts(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) String searchTerm) {
        return productService.filterProducts(categoryId, brandId, searchTerm);
    }

    /**     * Update a product.
     */
    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id,
            @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    /**
     * Delete a product.
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }

    /**
     * Create a new product category.
     */
    @PostMapping("/category")
    public CategoryResponse createCategory(@RequestBody CategoryRequest request) {
        return productService.createCategory(request);
    }

    /**
     * Create multiple product categories.
     */
    @PostMapping("/category/bulk")
    public String createAllCategories(@RequestBody List<CategoryRequest> requests) {
        for (CategoryRequest request : requests) {
            productService.createCategory(request);
        }
        return "done";
    }

    /**
     * Get all product categories.
     */
    @GetMapping("/categories")
    public List<CategoryResponse> getAllCategories() {
        return productService.getAllCategories();
    }

    /**
     * Get only active product categories (for inventory dropdown).
     */
    @GetMapping("/categories/active")
    public List<CategoryResponse> getActiveCategories() {
        return productService.getActiveCategories();
    }

    /**
     * Toggle category active/inactive status (admin only).
     */
    @PutMapping("/categories/{id}/toggle")
    public CategoryResponse toggleCategoryStatus(@PathVariable Long id) {
        return productService.toggleCategoryStatus(id);
    }

    /**
     * Update category active status (admin only).
     */
    @PutMapping("/categories/{id}/status")
    public CategoryResponse updateCategoryStatus(@PathVariable Long id, @RequestParam Boolean isActive) {
        return productService.updateCategoryStatus(id, isActive);
    }

    /**
     * Update category (name/properties).
     */
    @PutMapping("/categories/{id}")
    public CategoryResponse updateCategory(@PathVariable Long id, @RequestBody CategoryRequest request) {
        return productService.updateCategory(id, request);
    }

    /**
     * Create a new product brand.
     */
    @PostMapping("/brand")
    public BrandResponse createBrand(@RequestBody BrandRequest request) {
        return productService.createBrand(request);
    }

    /**
     * Get all product brands.
     */
    @GetMapping("/brands")
    public List<BrandResponse> getAllBrands() {
        return productService.getAllBrands();
    }

    /**
     * Get only active product brands (for dropdowns).
     */
    @GetMapping("/brands/active")
    public List<BrandResponse> getActiveBrands() {
        return productService.getActiveBrands();
    }

    /**
     * Get brands by category.
     */
    @GetMapping("/brands/category/{category}")
    public List<String> getBrandsByCategory(@PathVariable String category) {
        return productService.getBrandsByCategory(category);
    }

    /**
     * Update a brand.
     */
    @PutMapping("/brand/{id}")
    public BrandResponse updateBrand(@PathVariable Long id, @RequestBody BrandRequest request) {
        return productService.updateBrand(id, request);
    }

    /**
     * Toggle brand active/inactive status.
     */
    @PutMapping("/brand/{id}/toggle")
    public BrandResponse toggleBrandStatus(@PathVariable Long id) {
        return productService.toggleBrandStatus(id);
    }

    /**
     * Delete a brand.
     */
    @DeleteMapping("/brand/{id}")
    public void deleteBrand(@PathVariable Long id) {
        productService.deleteBrand(id);
    }

    /* ==================================
       CATEGORY-BRAND MAPPING ENDPOINTS
       ================================== */

    /**
     * Get all brands mapped to a category.
     */
    @GetMapping("/categories/{categoryId}/brands")
    public List<BrandResponse> getBrandsByCategory(@PathVariable Long categoryId) {
        List<Brand> brands = categoryBrandMappingService.getBrandsByCategory(categoryId);
        return brands.stream()
                .map(b -> new BrandResponse(b.getId(), b.getName(), b.getNameHi(), b.getIsActive()))
                .toList();
    }

    /**
     * Map a brand to a category.
     */
    @PostMapping("/categories/brands/map")
    public String mapBrandToCategory(@RequestBody CategoryBrandMappingRequest request) {
        categoryBrandMappingService.mapBrandToCategory(request.getCategoryId(), request.getBrandId());
        return "Brand mapped successfully";
    }

    /**
     * Unmap a brand from a category.
     */
    @DeleteMapping("/categories/brands/unmap")
    public String unmapBrandFromCategory(@RequestBody CategoryBrandMappingRequest request) {
        categoryBrandMappingService.unmapBrandFromCategory(request.getCategoryId(), request.getBrandId());
        return "Brand unmapped successfully";
    }

    /**
     * Check if a brand is mapped to a category.
     */
    @GetMapping("/categories/{categoryId}/brands/{brandId}")
    public boolean isBrandMappedToCategory(@PathVariable Long categoryId, @PathVariable Long brandId) {
        return categoryBrandMappingService.isBrandMappedToCategory(categoryId, brandId);
    }

        /**
         * Generate a Code128 barcode PNG for a given SKU.
         */
        @GetMapping(value = "/sku/{sku}/barcode", produces = MediaType.IMAGE_PNG_VALUE)
        public ResponseEntity<byte[]> barcodeForSku(@PathVariable String sku) throws Exception {
            int width = 400;
            int height = 100;

            BitMatrix bitMatrix = new MultiFormatWriter().encode(sku, BarcodeFormat.CODE_128, width, height);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", baos);
            byte[] img = baos.toByteArray();
            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(img);
        }

}
