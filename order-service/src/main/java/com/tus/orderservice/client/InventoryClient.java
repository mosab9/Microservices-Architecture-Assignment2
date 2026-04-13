package com.tus.orderservice.client;

import com.tus.orderservice.exception.InventoryServiceException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class InventoryClient {

    private final RestTemplate restTemplate;
    private final String inventoryServiceUrl;
    private final String gatewaySecret;

    private static final String GATEWAY_SECRET_HEADER = "X-Gateway-Secret";

    public InventoryClient(RestTemplate restTemplate,
                           @Value("${inventory.service.url}") String inventoryServiceUrl,
                           @Value("${gateway.secret}") String gatewaySecret) {
        this.restTemplate = restTemplate;
        this.inventoryServiceUrl = inventoryServiceUrl;
        this.gatewaySecret = gatewaySecret;
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(GATEWAY_SECRET_HEADER, gatewaySecret);
        headers.set("Content-Type", "application/json");
        return headers;
    }

    @CircuitBreaker(name = "inventoryService", fallbackMethod = "checkAvailabilityFallback")
    @Retry(name = "inventoryService")
    public InventoryAvailabilityResponse checkAvailability(
            List<InventoryAvailabilityRequest.InventoryItemRequest> items) {
        String url = inventoryServiceUrl + "/api/inventory/check-availability";
        InventoryAvailabilityRequest request = new InventoryAvailabilityRequest(items);
        HttpEntity<InventoryAvailabilityRequest> entity = new HttpEntity<>(request, createHeaders());

        log.info("=== INVENTORY CLIENT: Checking availability for {} items ===", items.size());
        log.info("=== INVENTORY CLIENT: Calling URL: {} ===", url);

        try {
            InventoryAvailabilityResponse response = restTemplate.postForObject(
                    url, entity, InventoryAvailabilityResponse.class);

            log.info("=== INVENTORY CLIENT: Availability check result: allAvailable={} ===",
                    response != null ? response.allAvailable() : "null");
            return response;
        } catch (Exception e) {
            log.error("=== INVENTORY CLIENT: Failed to call inventory service: {} - {} ===",
                    e.getClass().getSimpleName(), e.getMessage());
            throw e;
        }
    }

    private InventoryAvailabilityResponse checkAvailabilityFallback(
            List<InventoryAvailabilityRequest.InventoryItemRequest> items, Throwable t) {
        log.warn("Circuit breaker fallback for checkAvailability: {}", t.getMessage());
        throw new InventoryServiceException("Inventory service is currently unavailable", t);
    }

    @CircuitBreaker(name = "inventoryService", fallbackMethod = "reserveStockFallback")
    @Retry(name = "inventoryService")
    public InventoryReservationResponse reserveStock(Long orderId,
            List<InventoryAvailabilityRequest.InventoryItemRequest> items) {
        String url = inventoryServiceUrl + "/api/inventory/reserve";
        InventoryReserveRequest request = new InventoryReserveRequest(orderId, items);
        HttpEntity<InventoryReserveRequest> entity = new HttpEntity<>(request, createHeaders());

        log.info("Reserving stock for order {}: {} items", orderId, items.size());
        InventoryReservationResponse response = restTemplate.postForObject(
                url, entity, InventoryReservationResponse.class);

        log.info("Reservation result for order {}: success={}", orderId,
                response != null ? response.success() : "null");
        return response;
    }

    private InventoryReservationResponse reserveStockFallback(Long orderId,
            List<InventoryAvailabilityRequest.InventoryItemRequest> items, Throwable t) {
        log.warn("Circuit breaker fallback for reserveStock (order {}): {}", orderId, t.getMessage());
        throw new InventoryServiceException("Inventory service is currently unavailable", t);
    }

    public void confirmReservation(Long orderId, Long customerId,
            List<OrderCreatedEvent.OrderItemEvent> items) {
        try {
            String url = inventoryServiceUrl + "/api/events/order-created";
            OrderCreatedEvent event = new OrderCreatedEvent(
                    orderId, customerId, items, LocalDateTime.now());
            HttpEntity<OrderCreatedEvent> entity = new HttpEntity<>(event, createHeaders());

            log.info("Sending order-created event for order {}", orderId);
            restTemplate.postForObject(url, entity, Void.class);
            log.info("Order-created event sent successfully for order {}", orderId);
        } catch (RestClientException e) {
            log.error("Failed to send order-created event for order {}: {}", orderId, e.getMessage());
            throw new InventoryServiceException("Failed to confirm reservation", e);
        }
    }

    public void cancelReservation(Long orderId, String reason) {
        try {
            String url = inventoryServiceUrl + "/api/events/order-cancelled";
            OrderCancelledEvent event = new OrderCancelledEvent(
                    orderId, reason, LocalDateTime.now());
            HttpEntity<OrderCancelledEvent> entity = new HttpEntity<>(event, createHeaders());

            log.info("Sending order-cancelled event for order {}", orderId);
            restTemplate.postForObject(url, entity, Void.class);
            log.info("Order-cancelled event sent successfully for order {}", orderId);
        } catch (RestClientException e) {
            log.error("Failed to send order-cancelled event for order {}: {}", orderId, e.getMessage());
            // Don't throw - cancellation notification failure shouldn't block order cancellation
            log.warn("Inventory service will need to handle orphaned reservation for order {}", orderId);
        }
    }
}
