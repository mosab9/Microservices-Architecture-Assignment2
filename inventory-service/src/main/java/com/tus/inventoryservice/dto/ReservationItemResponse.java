package com.tus.inventoryservice.dto;

import com.tus.inventoryservice.entity.StockReservation;

public record ReservationItemResponse(
        Long reservationId,
        Long productId,
        String productName,
        Integer quantityReserved,
        String status
) {
    public static ReservationItemResponse from(StockReservation reservation) {
        return new ReservationItemResponse(
                reservation.getId(),
                reservation.getInventoryItem().getId(),
                reservation.getInventoryItem().getProductName(),
                reservation.getQuantityReserved(),
                reservation.getStatus().name()
        );
    }
}
