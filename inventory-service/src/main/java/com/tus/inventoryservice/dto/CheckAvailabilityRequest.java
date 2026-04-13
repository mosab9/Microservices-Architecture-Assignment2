package com.tus.inventoryservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CheckAvailabilityRequest(
        @NotEmpty(message = "Items list cannot be empty")
        @Valid
        List<AvailabilityItemRequest> items
) {}
