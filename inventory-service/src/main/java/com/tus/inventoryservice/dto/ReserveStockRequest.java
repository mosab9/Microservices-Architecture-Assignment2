package com.tus.inventoryservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReserveStockRequest(
        @NotNull(message = "Order ID is required")
        Long orderId,

        @NotEmpty(message = "Items list cannot be empty")
        @Valid
        List<AvailabilityItemRequest> items
) {}
