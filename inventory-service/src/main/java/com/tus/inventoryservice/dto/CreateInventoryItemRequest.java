package com.tus.inventoryservice.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CreateInventoryItemRequest(
        @NotBlank(message = "Product name is required")
        String productName,

        @NotBlank(message = "Description is required")
        String description,

        @NotNull(message = "Quantity is required")
        @Min(value = 0, message = "Quantity must be at least 0")
        Integer quantityAvailable,

        @NotNull(message = "Unit price is required")
        @DecimalMin(value = "0.01", message = "Unit price must be at least 0.01")
        BigDecimal unitPrice
) {}
