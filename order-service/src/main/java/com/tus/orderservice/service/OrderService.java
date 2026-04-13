package com.tus.orderservice.service;


import com.tus.orderservice.client.*;
import com.tus.orderservice.dto.*;
import com.tus.orderservice.entity.*;
import com.tus.orderservice.exception.*;
import com.tus.orderservice.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final InventoryClient inventoryClient;

    public OrderService(OrderRepository orderRepository,
                        CustomerRepository customerRepository,
                        InventoryClient inventoryClient) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.inventoryClient = inventoryClient;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Customer", request.getCustomerId()));

        // Build inventory items for availability check
        List<InventoryAvailabilityRequest.InventoryItemRequest> inventoryItems = request.getItems().stream()
                .map(item -> new InventoryAvailabilityRequest.InventoryItemRequest(
                        item.getProductId(),
                        item.getQuantity()))
                .toList();

        // Check availability first
        InventoryAvailabilityResponse availability = inventoryClient.checkAvailability(inventoryItems);
        if (!availability.allAvailable()) {
            String unavailableItems = availability.items().stream()
                    .filter(item -> !item.available())
                    .map(item -> String.format("%s (requested: %d, available: %d)",
                            item.productName(), item.requestedQuantity(), item.availableStock()))
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("unknown items");
            throw new InvalidOrderStateException("Insufficient stock for: " + unavailableItems);
        }

        // Create order items
        List<OrderItem> items = request.getItems().stream()
                .map(itemReq -> OrderItem.builder()
                        .productId(itemReq.getProductId())
                        .productName(itemReq.getProductName())
                        .quantity(itemReq.getQuantity())
                        .unitPrice(itemReq.getUnitPrice())
                        .build())
                .toList();

        BigDecimal total = items.stream()
                .map(i -> i.getUnitPrice()
                        .multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = Order.builder()
                .customer(customer)
                .totalPrice(total)
                .items(items)
                .build();

        items.forEach(item -> item.setOrder(order));
        Order savedOrder = orderRepository.save(order);

        // Reserve stock in inventory service
        try {
            InventoryReservationResponse reservation = inventoryClient.reserveStock(
                    savedOrder.getId(), inventoryItems);
            if (!reservation.success()) {
                // Rollback order creation if reservation fails
                orderRepository.delete(savedOrder);
                throw new InvalidOrderStateException("Failed to reserve stock: " + reservation.message());
            }
            log.info("Stock reserved for order {}", savedOrder.getId());
        } catch (InventoryServiceException e) {
            // Rollback order creation if inventory service is unavailable
            orderRepository.delete(savedOrder);
            throw e;
        }

        return OrderResponse.from(savedOrder);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public PagedResponse<OrderResponse> getAllOrders(int page, int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by("createdAt").descending());
        Page<Order> orderPage = orderRepository.findAll(pageable);
        List<OrderResponse> data = orderPage.getContent().stream()
                .map(OrderResponse::from).toList();
        return PagedResponse.of(data, page, size, orderPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public PagedResponse<OrderResponse> getOrdersByCustomer(
            Long customerId, int page, int size) {
        if (!customerRepository.existsById(customerId))
            throw new ResourceNotFoundException("Customer", customerId);
        Pageable pageable = PageRequest.of(page, size,
                Sort.by("createdAt").descending());
        Page<Order> orderPage = orderRepository
                .findByCustomerId(customerId, pageable);
        List<OrderResponse> data = orderPage.getContent().stream()
                .map(OrderResponse::from).toList();
        return PagedResponse.of(data, page, size, orderPage.getTotalElements());
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long id,
                                           UpdateOrderStatusRequest request) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));

        OrderStatus newStatus;
        try {
            newStatus = OrderStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidOrderStateException(
                    "Invalid status: " + request.getStatus());
        }

        if (order.getStatus() == OrderStatus.CANCELLED)
            throw new InvalidOrderStateException(
                    "Cannot update a cancelled order");

        if (order.getStatus() == OrderStatus.CONFIRMED
                && newStatus == OrderStatus.PENDING)
            throw new InvalidOrderStateException(
                    "A confirmed order cannot be reverted to PENDING");

        OrderStatus previousStatus = order.getStatus();
        order.setStatus(newStatus);
        Order savedOrder = orderRepository.save(order);

        // Notify inventory service of status change
        if (newStatus == OrderStatus.CONFIRMED && previousStatus == OrderStatus.PENDING) {
            // Order confirmed - finalize stock deduction
            List<OrderCreatedEvent.OrderItemEvent> itemEvents = order.getItems().stream()
                    .map(item -> new OrderCreatedEvent.OrderItemEvent(
                            item.getProductId(),
                            item.getProductName(),
                            item.getQuantity()))
                    .toList();
            inventoryClient.confirmReservation(order.getId(), order.getCustomer().getId(), itemEvents);
            log.info("Stock deduction confirmed for order {}", order.getId());
        } else if (newStatus == OrderStatus.CANCELLED) {
            // Order cancelled - release reserved stock
            inventoryClient.cancelReservation(order.getId(), "Order cancelled by user");
            log.info("Stock released for cancelled order {}", order.getId());
        }

        return OrderResponse.from(savedOrder);
    }

    @Transactional
    public OrderResponse updateOrder(Long id, CreateOrderRequest request) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));

        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Customer", request.getCustomerId()));

        // Cannot update cancelled orders
        if (order.getStatus() == OrderStatus.CANCELLED)
            throw new InvalidOrderStateException(
                    "Cannot update a cancelled order");

        List<OrderItem> items = request.getItems().stream()
                .map(itemReq -> OrderItem.builder()
                        .productId(itemReq.getProductId())
                        .productName(itemReq.getProductName())
                        .quantity(itemReq.getQuantity())
                        .unitPrice(itemReq.getUnitPrice())
                        .build())
                .toList();

        BigDecimal total = items.stream()
                .map(i -> i.getUnitPrice()
                        .multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        order.setCustomer(customer);
        order.setTotalPrice(total);
        order.getItems().clear();
        order.setItems(items);
        items.forEach(item -> item.setOrder(order));

        return OrderResponse.from(orderRepository.save(order));
    }

    @Transactional
    public void deleteOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));

        if (order.getStatus() == OrderStatus.CONFIRMED)
            throw new InvalidOrderStateException(
                    "Cannot delete a confirmed order");

        // Release reserved stock if order was pending
        if (order.getStatus() == OrderStatus.PENDING) {
            inventoryClient.cancelReservation(order.getId(), "Order deleted");
            log.info("Stock released for deleted order {}", order.getId());
        }

        orderRepository.deleteById(id);
    }
}
