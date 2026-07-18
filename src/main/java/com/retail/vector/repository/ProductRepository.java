package com.retail.vector.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.retail.vector.entity.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);

    Optional<Product> findByExternalBarcode(String externalBarcode);

    boolean existsBySku(String sku);

    boolean existsByExternalBarcode(String externalBarcode);

    @Query("SELECT DISTINCT p.brandId FROM Product p WHERE p.category = :category")
    List<Long> findByCategory(String category);

    @Query("SELECT DISTINCT p.sku FROM Product p WHERE p.brandId = :brandId")
    List<String> findByBrand(Long brandId);

    @Query("SELECT DISTINCT p.category FROM Product p")
    List<String> getAllCategories();

    Optional<Product> findByName(String name);

    //========================================================================================================
    @Query("SELECT p FROM Product p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Product> fetchByName(String name);

    @Query(value = "SELECT * FROM product p WHERE LOWER(REPLACE(REPLACE(REPLACE(REPLACE(p.name, ' ', ''), '-', ''), '–', ''), '—', '')) LIKE LOWER(CONCAT('%', :norm, '%'))", nativeQuery = true)
    List<Product> fetchByNameNormalized(String norm);

    //========================================================================================================
    @Query(value = " SELECT p FROM Product p WHERE LOWER(p.nameHi) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Product> fetchByHindiName(String name);

    @Query("""
       SELECT p
       FROM Product p
       WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%'))
          OR LOWER(p.nameHi) LIKE LOWER(CONCAT('%', :name, '%'))
       """)
    List<Product> fetchByNameOrHindiName(String name);

    @Query(value = """
       SELECT *
       FROM product p
       WHERE REPLACE(REPLACE(REPLACE(REPLACE(p.nameHi,' ',''),'-',''),'–',''),'—','')
             LIKE CONCAT('%', :norm, '%')
       """, nativeQuery = true)
    List<Product> fetchByHindiNameNormalized(String norm);

    @Query(value = """
       SELECT *
       FROM product p
       WHERE LOWER(
              REPLACE(REPLACE(REPLACE(REPLACE(p.name,' ',''),'-',''),'–',''),'—','')
       ) LIKE LOWER(CONCAT('%', :norm, '%'))
       OR LOWER(
              REPLACE(REPLACE(REPLACE(REPLACE(p.nameHi,' ',''),'-',''),'–',''),'—','')
       ) LIKE LOWER(CONCAT('%', :norm, '%'))
       """, nativeQuery = true)
     List<Product> fetchByNameOrHindiNameNormalized(String norm);

    @Query("SELECT p FROM Product p WHERE p.sku = :sku")
    Product fetchBySku(String sku);

    // Find all products by status
    @Query("SELECT p FROM Product p WHERE p.status = :status")
    List<Product> findByStatus(String status);
}
