package com.tus.inventoryservice.dto;

import java.util.List;

public record AvailabilityResponse(
        Boolean allAvailable,
        List<AvailabilityItemResponse> items
) {
    public static AvailabilityResponse from(List<AvailabilityItemResponse> items) {
        boolean allAvailable = items.stream()
                .allMatch(AvailabilityItemResponse::available);
        return new AvailabilityResponse(allAvailable, items);
    }
}
