package com.retail.vector.service;

import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.retail.vector.client.AiServiceClient;
import com.retail.vector.dto.BrandRequest;
import com.retail.vector.dto.BrandResponse;
import com.retail.vector.dto.CategoryRequest;
import com.retail.vector.dto.CategoryResponse;
import com.retail.vector.dto.EmbeddingRequest;
import com.retail.vector.dto.EmbeddingResponse;
import com.retail.vector.dto.ProductRequest;
import com.retail.vector.dto.ProductResponse;
import com.retail.vector.entity.Product;
import com.retail.vector.exception.ProductException;
import com.retail.vector.repository.ProductRepository;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.PointsSelector;
import io.qdrant.client.grpc.Points.UpdateResult;
import lombok.RequiredArgsConstructor;



@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final BrandService brandService;
    private final AiServiceClient aiServiceClient;

    private final QdrantClient qdrantClient;

    @Value("${qdrant.collection-name}")
    private  String collectionName;

    /**
     * Creates a new product, generates its SKU, checks for uniqueness, generates a barcode, saves it to the database, and triggers embedding generation and Qdrant upsert.
     * @param request
     * @return
     */

    public ProductResponse create(ProductRequest request) {

        // Get brand name from brand ID
        String brandName = null;
        if (request.getBrandId() != null) {
            try {
                brandName = brandService.getById(request.getBrandId()).getBrand();
            } catch (Exception e) {
                throw new ProductException("Invalid brand ID");
            }
        }

        String sku = generateSku(
            brandName,
            request.getCategory(),
            request.getName(),
            request.getUnit(),
            request.isLoose(),
            request.getProductSize(),
            request.getPacketSize(),
            request.getPacketUnit());

        if (productRepository.existsBySku(sku)) {
            throw new ProductException("Product SKU already exists");
        }

        // Check if external barcode already exists (if provided)
        if (request.getExternalBarcode() != null && !request.getExternalBarcode().isEmpty()) {
            if (productRepository.existsByExternalBarcode(request.getExternalBarcode())) {
                throw new ProductException("External barcode already exists");
            }
        }

        // generate barcode PNG for SKU
        byte[] barcodeBytes = null;
        try {
            int bw = 400;
            int bh = 100;
            BitMatrix bitMatrix = new MultiFormatWriter().encode(sku, BarcodeFormat.CODE_128, bw, bh);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", baos);
            barcodeBytes = baos.toByteArray();
        } catch (Exception e) {
            // non-fatal — proceed without barcode
            barcodeBytes = null;
        }

        Product product = productRepository.save(Product.builder()
                .sku(sku)
                .externalBarcode(request.getExternalBarcode())
                .name(request.getName())
                .nameHi(request.getNameHi())
                .description(request.getDescription())
                .category(request.getCategory())
                .categoryHi(request.getCategoryHi())
                .brandId(request.getBrandId())
                .unit(request.getUnit())
                .loose(request.isLoose())
            .productSize(request.getProductSize())
            .packetSize(request.getPacketSize())
            .packetUnit(request.getPacketUnit())
                .price(request.getPrice())
                .discountAmount(request.getDiscountAmount())
                .status(request.getStatus())
                .barcode(barcodeBytes)
                .build());

        ProductResponse response = mapToResponse(product);
        genrateEmbeddingAndTriggerQdrantUpsert(response);
     
      return response;
    }

    /**
     * Generates an embedding for the product data and triggers an upsert to the Qdrant collection. It builds the embedding prompt from the product response, creates the embedding using the AI service client, and then constructs the Qdrant payload to save it to the collection.
     * @param response
     */
    public void genrateEmbeddingAndTriggerQdrantUpsert(ProductResponse response) {
         //Get embedding for the product name and save it to the database
       String embeddingPrompt = this.buildDataEmbedding(response);
       ResponseEntity<EmbeddingResponse> embeddingResponse = aiServiceClient.createEmbedding(new EmbeddingRequest(embeddingPrompt));
       List<Double> embedding=embeddingResponse.getBody().getEmbedding();
       this.buildQdrantPayloadAndSaveQdrant(response, embedding);
    }

    /**
     * Builds the Qdrant payload from the product response and embedding, and saves it to the Qdrant collection.
     * @param response
     * @param embedding
     */
    public void buildQdrantPayloadAndSaveQdrant(ProductResponse response, List<Double> embedding) {
        try {
            float[] vectorArray = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                vectorArray[i] = embedding.get(i).floatValue();
            }

           Map<String, io.qdrant.client.grpc.JsonWithInt.Value> payloadMap = new HashMap<>();


            payloadMap.put("productId", value(response.getId()));
            payloadMap.put("sku", value(response.getSku()));
            payloadMap.put("name", value(response.getName()));
            payloadMap.put("nameHi", value(response.getNameHi()));
            payloadMap.put("brandId", value(response.getBrandId()));
            payloadMap.put("brandName", value(response.getBrandName()));
            payloadMap.put("brandNameHi", value(response.getBrandNameHi()));
            payloadMap.put("category", value(response.getCategory()));
            payloadMap.put("categoryHi", value(response.getCategoryHi()));
            payloadMap.put("isLoose", value(response.isLoose()));
            if(response.isLoose()){
                payloadMap.put("productType", value("loose"));
                payloadMap.put("unit", value(response.getUnit()));
                payloadMap.put("productSize", value(response.getProductSize()));
            }else{
                payloadMap.put("productType", value("packet"));
                payloadMap.put("unit", value(response.getPacketUnit()));
                payloadMap.put("productSize", value(response.getPacketSize()));
            }
                     
            payloadMap.put("price", value(response.getPrice()));
            payloadMap.put("status", value(response.getStatus()));

            PointStruct point = PointStruct.newBuilder()
                    .setId(id(response.getId()))
                    .setVectors(vectors(vectorArray))
                    .putAllPayload(payloadMap)
                    .build();

            // Uses the dynamically loaded collection name
            UpdateResult result = qdrantClient.upsertAsync(collectionName, List.of(point)).get();
            System.out.println("Qdrant Upsert Status: " + result.getStatus());
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Failed to upsert payload to Qdrant: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Builds a string representation of the product data for embedding generation.
     * @param response
     * @return
     */
     public String buildDataEmbedding(ProductResponse response){
        
        Double size = null;
        String productType = null;

         if (response.isLoose()) {
             size = response.getProductSize();
             productType= " loose";
         } else {
             size = response.getPacketSize();
             productType= " packet";
         }

         String embeddingText = String.format(
                 "Brand %s. Product %s. Hindi product %s. Category %s. Hindi category %s. Size %s %s. isLoose: %s. productType: %s.",
                 response.getBrandName(), response.getName(), response.getNameHi(), response.getCategory(),
                 response.getCategoryHi(),
                 size,
                 response.getUnit(),
                 response.isLoose(),
                 productType);
         return embeddingText;
     }


     /**
      * Retrieves all products.
      * @return
      */
    public List<ProductResponse> getAll() {
        return productRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Retrieves a product by its ID.
     * @param id
     * @return
     */
    public ProductResponse getById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductException("Product not found"));
        return mapToResponse(product);
    }

    /**
     * Retrieves products by name and language. If the language is "en", it searches by English name; if "hi", it searches by Hindi name; otherwise, it searches both. It also provides fallback normalized searches.   
     * @param name
     * @param language
     * @return
     */
    public List<ProductResponse> getByName(String name, String language) {

        if (name == null || name.isBlank()) {
            return List.of();
        }

        String searchNames = name;
        List<Product> products = List.of();
        
            String searchName = searchNames.trim();
            products = productRepository.fetchByName(searchName);

            // Fallback normalized English search
            if (products.isEmpty()) {
                String norm = searchName.replaceAll("[\\s\\-–—_]", "").toLowerCase();
                if (!norm.isBlank()) {
                    products = productRepository.fetchByNameNormalized(norm);
                }
            }

    
        return products.stream()
                .map(this::mapToResponse)
                .toList();
    }

    public ProductResponse getAllSku(String sku) {

        Product product = productRepository.fetchBySku(sku);
        if (product == null) {
            throw new ProductException("Product not found");
        }
        return mapToResponse(product);
    }

    /**
     * Search product by barcode (either SKU or external barcode)
     * This method works for both SKU scanning and external barcode scanning
     */
    public ProductResponse searchByBarcode(String barcode) {
        if (barcode == null || barcode.isEmpty()) {
            throw new ProductException("Barcode cannot be empty");
        }

        // First try to find by external barcode
        var productByExternalBarcode = productRepository.findByExternalBarcode(barcode);
        if (productByExternalBarcode.isPresent()) {
            return mapToResponse(productByExternalBarcode.get());
        }

        // If not found, try to find by SKU
        Product product = productRepository.fetchBySku(barcode);
        if (product != null) {
            return mapToResponse(product);
        }

        throw new ProductException("Product not found for barcode: " + barcode);
    }

    /**
     * Retrieves products by category. It fetches brand IDs associated with the category and converts them to brand names. If no products are found, it throws a ProductException.
     * @param category
     * @return
     */

    public List<String> getByCategory(String category) {
        List<Long> brandIds = productRepository.findByCategory(category);
        if (brandIds.isEmpty()) {
            throw new ProductException("No products found");
        }
        // Convert brand IDs to brand names
        return brandIds.stream()
                .map(id -> {
                    try {
                        return brandService.getById(id).getBrand();
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(name -> name != null)
                .toList();
    }
    /**
     * Retrieves products by brand ID. It fetches SKUs associated with the brand. If no products are found, it throws a ProductException.
     * @param brandId
     * @return
     */
    public List<String> getByBrand(Long brandId) {
        List<String> skus = productRepository.findByBrand(brandId);
        if (skus.isEmpty()) {
            throw new ProductException("No products found for this brand");
        }
        return skus;
    }

    /**
     * Filter products by category, brand, and search term (for shopping/customer
     * view)
     * All parameters are optional. Returns only ACTIVE products.
     */
    public List<ProductResponse> filterProducts(Long categoryId, Long brandId, String searchTerm) {
        // Start with all ACTIVE products
        List<Product> products = productRepository.findByStatus("ACTIVE");

        // Filter by category if provided (must have matching brand in category mapping)
        if (categoryId != null && categoryId > 0) {
            // For now, we'll filter by products that have a brand
            // In a real scenario, we'd filter by products in categories
            products = products.stream()
                    .filter(p -> p.getBrandId() != null)
                    .toList();
        }

        // Filter by brand if provided
        if (brandId != null && brandId > 0) {
            products = products.stream()
                    .filter(p -> p.getBrandId() != null && p.getBrandId().equals(brandId))
                    .toList();
        }

        // Filter by search term if provided (search in name, description, SKU)
        if (searchTerm != null && !searchTerm.isEmpty()) {
            String lowerSearchTerm = searchTerm.toLowerCase();
            products = products.stream()
                    .filter(p -> p.getName().toLowerCase().contains(lowerSearchTerm) ||
                            (p.getDescription() != null && p.getDescription().toLowerCase().contains(lowerSearchTerm))
                            ||
                            p.getSku().toLowerCase().contains(lowerSearchTerm))
                    .toList();
        }

        return products.stream()
                .map(this::mapToResponse)
                .toList();
    }
    /**
     * Updates an existing product by its ID. It checks for uniqueness of the external barcode if it's being updated, updates the product fields, saves it to the database, and triggers embedding generation and Qdrant upsert.
     * @param id
     * @param request
     * @return
     */
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductException("Product not found"));

        // Check if external barcode is being updated and if it already exists elsewhere
        if (request.getExternalBarcode() != null && !request.getExternalBarcode().isEmpty()) {
            String currentBarcode = product.getExternalBarcode();
            if (!request.getExternalBarcode().equals(currentBarcode) &&
                    productRepository.existsByExternalBarcode(request.getExternalBarcode())) {
                throw new ProductException("External barcode already exists");
            }
            product.setExternalBarcode(request.getExternalBarcode());
        }

        product.setName(request.getName());
        product.setNameHi(request.getNameHi());
        product.setDescription(request.getDescription());
        product.setCategory(request.getCategory());
        product.setCategoryHi(request.getCategoryHi());
        product.setBrandId(request.getBrandId());
        product.setUnit(request.getUnit());
        product.setLoose(request.isLoose());
        product.setProductSize(request.getProductSize());
        product.setPacketSize(request.getPacketSize());
        product.setPacketUnit(request.getPacketUnit());
        product.setPrice(request.getPrice());
        product.setDiscountAmount(request.getDiscountAmount());
        product.setStatus(request.getStatus());

        ProductResponse response = mapToResponse(productRepository.save(product));
        genrateEmbeddingAndTriggerQdrantUpsert(response);
        return response;

    }

    /**
     * Deletes a product by its ID. It checks if the product exists, deletes it from the database, and also deletes it from the Qdrant collection.
     * @param id
     */

    public void delete(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ProductException("Product not found");
        }
        productRepository.deleteById(id);
        // delete from Qdrant as well
    
        try {
            // Pass the collection name and the list of wrapped IDs directly
            UpdateResult result = qdrantClient.deleteAsync(collectionName,List.of(id(id)) ).get();
            System.out.println("Product deletion status: " + result.getStatus());
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Failed to delete product from Qdrant: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Maps a Product entity to a ProductResponse DTO. It converts the barcode to Base64 and fetches the brand name based on the brand ID. If the brand is not found, it sets the brand name to "Unknown".
     * @param product
     * @return
     */

    private ProductResponse mapToResponse(Product product) {
        String barcodeBase64 = null;
        if (product.getBarcode() != null) {
            barcodeBase64 = java.util.Base64.getEncoder().encodeToString(product.getBarcode());
        }

        // Fetch brand name based on brand ID
        String brandName = null;
        if (product.getBrandId() != null) {
            try {
                brandName = brandService.getById(product.getBrandId()).getBrand();
            } catch (Exception e) {
                brandName = "Unknown";
            }
        }

        return ProductResponse.builder()
                .id(product.getId())
                .sku(product.getSku())
                .externalBarcode(product.getExternalBarcode())
                .name(product.getName())
                .nameHi(product.getNameHi())
                .description(product.getDescription())
                .category(product.getCategory())
                .categoryHi(product.getCategoryHi())
                .brandId(product.getBrandId())
                .brandName(brandName)
                .brandNameHi(getBrandNameHi(product.getBrandId()))
                .unit(product.getUnit())
                .productSize(product.getProductSize())
                .loose(product.isLoose())
                .packetSize(product.getPacketSize())
                .packetUnit(product.getPacketUnit())
                .price(product.getPrice())
                .discountAmount(product.getDiscountAmount())
                .status(product.getStatus())
                .barcode(barcodeBase64)
                .build();
    }

    /**
     * Fetches the Hindi name of a brand based on its ID. If the brand ID is null or if the brand is not found, it returns null.
     * @param brandId
     * @return
     */
    private String getBrandNameHi(Long brandId) {
        if (brandId == null) {
            return null;
        }
        try {
            return brandService.getById(brandId).getNameHi();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Creates a new category using the provided CategoryRequest. It delegates the creation to the CategoryService.
     * @param request
     * @return
     */

    public CategoryResponse createCategory(CategoryRequest request) {
        return categoryService.create(request);
    }

    /**
     * Retrieves all categories.
     * @return
     */
    public List<CategoryResponse> getAllCategories() {
        return categoryService.getAll();
    }

    /**
     * Retrieves all active categories.
     * @return
     */

    public List<CategoryResponse> getActiveCategories() {
        return categoryService.getActive();
    }

    /**
     * Toggles the active status of a category based on its ID. It delegates the operation to the CategoryService.
     * @param categoryId
     * @return
     */

    public CategoryResponse toggleCategoryStatus(Long categoryId) {
        return categoryService.toggleActive(categoryId);
    }


    /**
     * Updates the active status of a category based on its ID and the provided status. It delegates the operation to the CategoryService.
     * @param categoryId
     * @param isActive
     * @return
     */
    public CategoryResponse updateCategoryStatus(Long categoryId, Boolean isActive) {
        return categoryService.updateStatus(categoryId, isActive);
    }

    /**
     * Updates an existing category using the provided CategoryRequest. It delegates the update operation to the CategoryService.
     * @param categoryId
     * @param request
     * @return
     */

    public CategoryResponse updateCategory(Long categoryId, CategoryRequest request) {
        return categoryService.update(categoryId, request);
    }

    /**
     * Creates a new brand using the provided BrandRequest. It delegates the creation to the BrandService.
     * @param request
     * @return
     */
    public BrandResponse createBrand(BrandRequest request) {
        return brandService.create(request);
    }

    /**
     * Retrieves all brands.
     * @return
     */
    public List<BrandResponse> getAllBrands() {
        return brandService.getAll();
    }

    /**
     * Retrieves all active brands.
     * @return
     */
    public List<BrandResponse> getActiveBrands() {
        return brandService.getActive();
    }

    /**
     * Updates an existing brand using the provided BrandRequest. It delegates the update operation to the BrandService.
     * @param brandId
     * @param request
     * @return
     */
    public BrandResponse updateBrand(Long brandId, BrandRequest request) {
        return brandService.update(brandId, request);
    }

    /**
     * Toggles the active status of a brand based on its ID. It delegates the operation to the BrandService.
     * @param brandId
     * @return
     */
    public BrandResponse toggleBrandStatus(Long brandId) {
        return brandService.toggleActive(brandId);
    }

    /**
     * Deletes a brand based on its ID. It delegates the operation to the BrandService.
     * @param brandId
     */
    public void deleteBrand(Long brandId) {
        brandService.delete(brandId);
    }
    /**
     * Retrieves brands associated with a specific category. It fetches brand IDs from the product repository based on the category and converts them to brand names. If no brands are found for the category, it returns an empty list.
     * @param category
     * @return
     */

    public List<String> getBrandsByCategory(String category) {
        List<Long> brandIds = productRepository.findByCategory(category);
        // Convert brand IDs to brand names
        return brandIds.stream()
                .map(id -> {
                    try {
                        return brandService.getById(id).getBrand();
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(name -> name != null)
                .toList();
    }

    /**
     * Generates a SKU based on the provided product details. The SKU format is: [BrandCode]-[NameCode]-[Unit]-[TypeCode]-[Hash]. The method normalizes the input values, generates a short hash for uniqueness, and constructs the SKU string.
     * @param brand
     * @param category
     * @param name
     * @param unit
     * @param loose
     * @param productSize
     * @param packetSize
     * @param packetUnit
     * @return
     */

    public String generateSku(String brand, String category, String name, String unit, boolean loose, Double productSize, Double packetSize, String packetUnit) {

        String type = "";
       if(loose) {
            // when sold loose, include productSize and unit in SKU unit string if available
            if (productSize != null && unit != null) {
                unit = String.format("%s%s", productSize, unit);
            }
            type = "L";
        } else if ( packetSize != null && packetUnit != null) {
            unit = String.format("%s%s", packetSize, packetUnit);
            type= "P";

        }
       
       
       
        String base = String.join("|",
                normalize(brand),
                normalize(category),
                normalize(name),
                normalize(unit),
                normalize(type));

        String hash = shortHash(base);

        return String.format(
                "%s-%s-%s-%s-%s",
                shortCode(brand),
                shortCode(name),
                unit.toUpperCase(Locale.ROOT),
                shortCode(type),
                hash);
    }

    /**
     * Shortens a given string to a maximum of 6 characters, removing non-alphanumeric characters and converting to uppercase. If the input is null, it returns "NA".
     * @param value
     * @return
     */

    private String shortCode(String value) {
        if (value == null)
            return "NA";
        value = value.replaceAll("[^a-zA-Z0-9]", "");
        return value.length() <= 6
                ? value.toUpperCase()
                : value.substring(0, 6).toUpperCase();
    }

    /**
     * Normalizes a given string by trimming whitespace and converting to lowercase. If the input is null, it returns an empty string.
     * @param value
     * @return
     */

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Generates a short hash (6 hex characters) for a given input string using SHA-256. This is used for SKU generation to ensure uniqueness. If the hashing process fails, it throws a RuntimeException.
     * @param input
     * @return
     */
    private String shortHash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 3; i++) { // 6 hex chars
                hex.append(String.format("%02X", digest[i]));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SKU generation failed", e);
        }
    }

}
