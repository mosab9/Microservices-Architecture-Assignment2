package com.tus.inventoryservice.event;

import com.tus.inventoryservice.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Slf4j
public class OrderEventController {

    private final InventoryService inventoryService;

    @PostMapping("/order-created")
    public ResponseEntity<Void> handleOrderCreated(@RequestBody OrderCreatedEvent event) {
        log.info("Received OrderCreated event for order: {}", event.orderId());

        try {
            inventoryService.confirmReservation(event.orderId());
            log.info("Successfully finalized stock deduction for order: {}", event.orderId());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to process OrderCreated event for order {}: {}",
                    event.orderId(), e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/order-cancelled")
    public ResponseEntity<Void> handleOrderCancelled(@RequestBody OrderCancelledEvent event) {
        log.info("Received OrderCancelled event for order: {}", event.orderId());

        try {
            inventoryService.cancelReservation(event.orderId());
            log.info("Successfully released stock for cancelled order: {}", event.orderId());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to process OrderCancelled event for order {}: {}",
                    event.orderId(), e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
