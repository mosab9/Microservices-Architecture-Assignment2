package com.tus.orderservice.client;

import java.time.LocalDateTime;
import java.util.List;

public record OrderCreatedEvent(
        Long orderId,
        Long customerId,
        List<OrderItemEvent> items,
        LocalDateTime createdAt
) {
    public record OrderItemEvent(
            Long productId,
            String productName,
            Integer quantity
    ) {}
}
