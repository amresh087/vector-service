package com.retail.vector.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.retail.vector.dto.ProductRequest;
import com.retail.vector.dto.ProductResponse;
import com.retail.vector.service.VectorService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class VectorController {

    private final VectorService vectorService;


    
    @PostMapping
    public ProductResponse create(@RequestBody ProductRequest request) {

        return vectorService.create(request);
    }

   

}
