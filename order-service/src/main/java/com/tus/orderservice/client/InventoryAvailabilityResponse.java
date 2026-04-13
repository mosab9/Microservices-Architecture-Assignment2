package com.tus.orderservice.client;

import java.util.List;

public record InventoryAvailabilityResponse(
        Boolean allAvailable,
        List<InventoryItemResponse> items
) {
    public record InventoryItemResponse(
            Long productId,
            String productName,
            Integer requestedQuantity,
            Integer availableStock,
            Boolean available
    ) {}
}
