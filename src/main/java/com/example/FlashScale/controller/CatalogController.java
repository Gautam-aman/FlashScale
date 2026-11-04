package com.example.FlashScale.controller;

import com.example.FlashScale.dto.CatalogDtos;
import com.example.FlashScale.service.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CatalogController {

    @Autowired
    private final CatalogService service;

    @GetMapping("/products")
    public List<CatalogDtos.ProductResponse> getProducts() {
        return service.getProducts();
    }

    @GetMapping("/products/{id}")
    public CatalogDtos.ProductResponse getProduct(@PathVariable Long id) {
        return service.getProduct(id);
    }

    @GetMapping("/sales")
    public List<CatalogDtos.FlashSaleResponse> getSales() {
        return service.getSales();
    }

    @GetMapping("/sales/{id}")
    public CatalogDtos.FlashSaleResponse getSale(@PathVariable Long id) {
        return service.getSale(id);
    }

    @GetMapping("/sales/{id}/items")
    public List<CatalogDtos.SaleItemResponse> getSaleItems(
            @PathVariable Long id) {
        return service.getSaleItems(id);
    }

}

