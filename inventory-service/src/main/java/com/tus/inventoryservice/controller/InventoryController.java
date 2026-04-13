package com.tus.inventoryservice.controller;

import com.tus.inventoryservice.dto.*;
import com.tus.inventoryservice.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping
    public ResponseEntity<InventoryItemResponse> createInventoryItem(
            @Valid @RequestBody CreateInventoryItemRequest request) {
        InventoryItemResponse response = inventoryService.createInventoryItem(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<InventoryItemResponse> getInventoryItem(@PathVariable Long id) {
        InventoryItemResponse response = inventoryService.getInventoryItemById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<PagedResponse<InventoryItemResponse>> getAllInventoryItems(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PagedResponse<InventoryItemResponse> response = inventoryService.getAllInventoryItems(page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/available")
    public ResponseEntity<PagedResponse<InventoryItemResponse>> getAvailableItems(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PagedResponse<InventoryItemResponse> response = inventoryService.getAvailableItems(page, size);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<InventoryItemResponse> updateInventoryItem(
            @PathVariable Long id,
            @Valid @RequestBody CreateInventoryItemRequest request) {
        InventoryItemResponse response = inventoryService.updateInventoryItem(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInventoryItem(@PathVariable Long id) {
        inventoryService.deleteInventoryItem(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/stock")
    public ResponseEntity<InventoryItemResponse> updateStock(
            @PathVariable Long id,
            @RequestParam Integer quantityChange) {
        InventoryItemResponse response = inventoryService.updateStock(id, quantityChange);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/check-availability")
    public ResponseEntity<AvailabilityResponse> checkAvailability(
            @Valid @RequestBody CheckAvailabilityRequest request) {
        AvailabilityResponse response = inventoryService.checkAvailability(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reserve")
    public ResponseEntity<ReservationResponse> reserveStock(
            @Valid @RequestBody ReserveStockRequest request) {
        ReservationResponse response = inventoryService.reserveStock(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/confirm/{orderId}")
    public ResponseEntity<Void> confirmReservation(@PathVariable Long orderId) {
        inventoryService.confirmReservation(orderId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/cancel/{orderId}")
    public ResponseEntity<Void> cancelReservation(@PathVariable Long orderId) {
        inventoryService.cancelReservation(orderId);
        return ResponseEntity.ok().build();
    }
}
