package com.tus.orderservice.client;

import java.util.List;

public record InventoryReserveRequest(
        Long orderId,
        List<InventoryAvailabilityRequest.InventoryItemRequest> items
) {}
