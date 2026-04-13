package com.tus.orderservice.client;

import java.util.List;

public record InventoryReservationResponse(
        Long orderId,
        Boolean success,
        String message,
        List<ReservationItem> items
) {
    public record ReservationItem(
            Long reservationId,
            Long productId,
            String productName,
            Integer quantityReserved,
            String status
    ) {}
}
