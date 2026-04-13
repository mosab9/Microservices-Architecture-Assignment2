package com.tus.inventoryservice.dto;

public record AvailabilityItemResponse(
        Long productId,
        String productName,
        Integer requestedQuantity,
        Integer availableStock,
        Boolean available
) {}
