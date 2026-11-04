package com.example.FlashScale.service;

import com.example.FlashScale.Models.FlashSale;
import com.example.FlashScale.Models.FlashSaleItem;
import com.example.FlashScale.Models.Product;
import com.example.FlashScale.Repository.FlashSaleItemRepo;
import com.example.FlashScale.Repository.FlashSaleRepo;
import com.example.FlashScale.Repository.ProductRepo;
import com.example.FlashScale.dto.CatalogDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final ProductRepo products;
    private final FlashSaleRepo sales;
    private final FlashSaleItemRepo saleItems;

    public List<CatalogDtos.ProductResponse> getProducts() {
        return products.findAll(Sort.by("id"))
                .stream()
                .map(this::toProductResponse)
                .toList();
    }

    public CatalogDtos.ProductResponse getProduct(Long id) {
        return toProductResponse(
                products.findById(id).orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "Product not found"
                        )
                )
        );
    }

    public List<CatalogDtos.FlashSaleResponse> getSales() {
        return sales.findAll(Sort.by("id"))
                .stream()
                .map(this::toSaleResponse)
                .toList();
    }

    public CatalogDtos.FlashSaleResponse getSale(Long id) {
        return toSaleResponse(
                sales.findById(id).orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "Sale not found"
                        )
                )
        );
    }

    public List<CatalogDtos.SaleItemResponse> getSaleItems(Long saleId) {
        // Ensure unknown sale IDs return 404.
        if (!sales.existsById(saleId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Sale not found"
            );
        }

        return saleItems
                .findByFlashSale_IdOrderByIdAsc(saleId)
                .stream()
                .map(this::toSaleItemResponse)
                .toList();
    }

    private CatalogDtos.ProductResponse toProductResponse(Product product) {
        return new CatalogDtos.ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getPricePaise()
        );
    }

    private CatalogDtos.FlashSaleResponse toSaleResponse(FlashSale sale) {
        Instant now = Instant.now();

        String status;

        if (now.isBefore(sale.getStartsAt())) {
            status = "UPCOMING";
        } else if (now.isBefore(sale.getEndsAt())) {
            status = "ACTIVE";
        } else {
            status = "ENDED";
        }

        return new CatalogDtos.FlashSaleResponse(
                sale.getId(),
                sale.getTitle(),
                sale.getStartsAt(),
                sale.getEndsAt(),
                status
        );
    }

    private CatalogDtos.SaleItemResponse toSaleItemResponse(FlashSaleItem item) {

        Product product = item.getProduct();

        return new CatalogDtos.SaleItemResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                item.getSalePricePaise(),
                item.getTotalStock(),
                item.getAvailableStock()
        );
    }




}
