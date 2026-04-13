package com.tus.inventoryservice.dto;

import com.tus.inventoryservice.entity.InventoryItem;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InventoryItemResponse(
        Long id,
        String productName,
        String description,
        Integer quantityAvailable,
        Integer quantityReserved,
        Integer availableStock,
        BigDecimal unitPrice,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static InventoryItemResponse from(InventoryItem item) {
        return new InventoryItemResponse(
                item.getId(),
                item.getProductName(),
                item.getDescription(),
                item.getQuantityAvailable(),
                item.getQuantityReserved(),
                item.getAvailableStock(),
                item.getUnitPrice(),
                item.getActive(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}
