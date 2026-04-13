package com.tus.orderservice.client;

import java.util.List;

public record InventoryAvailabilityRequest(
        List<InventoryItemRequest> items
) {
    public record InventoryItemRequest(
            Long productId,
            Integer quantity
    ) {}
}
