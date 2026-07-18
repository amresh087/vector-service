package com.retail.vector.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.retail.vector.dto.BrandRequest;
import com.retail.vector.dto.BrandResponse;
import com.retail.vector.entity.Brand;
import com.retail.vector.repository.BrandRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;

    public BrandResponse create(BrandRequest request) {

        if (brandRepository.existsByNameIgnoreCase(request.getBrand())) {
            throw new RuntimeException("Brand already exists");
        }

        Brand brand = Brand.builder()
                .name(request.getBrand())
                .nameHi(request.getNameHi())
                .isActive(true)
                .build();

        brandRepository.save(brand);

        return new BrandResponse(brand.getId(), brand.getName(), brand.getNameHi(), brand.getIsActive());
    }

    // Get only active brands (for product dropdown)
    public List<BrandResponse> getActive() {
        return brandRepository.findByIsActiveTrue()
                .stream()
                .map(b -> new BrandResponse(b.getId(), b.getName(), b.getNameHi(), b.getIsActive()))
                .toList();
    }

    // Get all brands (for admin - including inactive)
    public List<BrandResponse> getAll() {
        return brandRepository.findAll()
                .stream()
                .map(b -> new BrandResponse(b.getId(), b.getName(), b.getNameHi(), b.getIsActive()))
                .toList();
    }

    // Get brand by ID
    public BrandResponse getById(Long brandId) {
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new RuntimeException("Brand not found"));
        return new BrandResponse(brand.getId(), brand.getName(), brand.getNameHi(), brand.getIsActive());
    }

    // Toggle brand active status (for admin)
    public BrandResponse toggleActive(Long brandId) {
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new RuntimeException("Brand not found"));

        brand.setIsActive(!brand.getIsActive());
        brandRepository.save(brand);

        return new BrandResponse(brand.getId(), brand.getName(), brand.getNameHi(), brand.getIsActive());
    }

    // Update brand active status
    public BrandResponse updateStatus(Long brandId, Boolean isActive) {
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new RuntimeException("Brand not found"));

        brand.setIsActive(isActive);
        brandRepository.save(brand);

        return new BrandResponse(brand.getId(), brand.getName(), brand.getNameHi(), brand.getIsActive());
    }

    // Update brand name
    public BrandResponse update(Long brandId, BrandRequest request) {
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new RuntimeException("Brand not found"));

        if (request.getBrand() != null && !request.getBrand().isEmpty()) {
            // Check if new name already exists (ignoring current brand)
            if (!brand.getName().equalsIgnoreCase(request.getBrand()) && 
                brandRepository.existsByNameIgnoreCase(request.getBrand())) {
                throw new RuntimeException("Brand name already exists");
            }
            brand.setName(request.getBrand());
        }
        if (request.getNameHi() != null) {
            brand.setNameHi(request.getNameHi());
        }

        brandRepository.save(brand);
        return new BrandResponse(brand.getId(), brand.getName(), brand.getNameHi(), brand.getIsActive());
    }

    // Delete a brand
    public void delete(Long brandId) {
        if (!brandRepository.existsById(brandId)) {
            throw new RuntimeException("Brand not found");
        }
        brandRepository.deleteById(brandId);
    }

    // Get brands by category (from product repository)
    public List<String> getBrandsByCategory(String category) {
        // This would need to be implemented in ProductService
        // as it queries the product table grouped by brand and category
        return List.of();
    }
}
