package com.example.FlashScale.dto;

import java.time.Instant;

public class CatalogDtos {

    public record ProductResponse(Long id, String sku, String name, Long pricePaise) {}

    public record FlashSaleResponse(Long id, String title, Instant startsAt, Instant endsAt, String status) {}

    public record SaleItemResponse(Long flashSaleItemId, Long productId, String sku, String name, Long salePricePaise, int totalStock, int availableStock) {}

}
