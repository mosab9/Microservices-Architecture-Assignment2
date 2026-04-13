package com.tus.inventoryservice.dto;

import java.util.List;

public record ReservationResponse(
        Long orderId,
        Boolean success,
        String message,
        List<ReservationItemResponse> items
) {
    public static ReservationResponse success(Long orderId, List<ReservationItemResponse> items) {
        return new ReservationResponse(orderId, true, "Stock reserved successfully", items);
    }

    public static ReservationResponse failure(Long orderId, String message) {
        return new ReservationResponse(orderId, false, message, List.of());
    }
}
