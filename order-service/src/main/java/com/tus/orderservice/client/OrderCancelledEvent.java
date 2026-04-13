package com.tus.orderservice.client;

import java.time.LocalDateTime;

public record OrderCancelledEvent(
        Long orderId,
        String reason,
        LocalDateTime cancelledAt
) {}
