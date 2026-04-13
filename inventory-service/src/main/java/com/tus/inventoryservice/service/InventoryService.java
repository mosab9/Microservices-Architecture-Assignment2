package com.tus.inventoryservice.service;

import com.tus.inventoryservice.dto.*;
import com.tus.inventoryservice.entity.InventoryItem;
import com.tus.inventoryservice.entity.ReservationStatus;
import com.tus.inventoryservice.entity.StockReservation;
import com.tus.inventoryservice.exception.DuplicateResourceException;
import com.tus.inventoryservice.exception.InsufficientStockException;
import com.tus.inventoryservice.exception.InvalidReservationStateException;
import com.tus.inventoryservice.exception.ResourceNotFoundException;
import com.tus.inventoryservice.repository.InventoryItemRepository;
import com.tus.inventoryservice.repository.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryItemRepository inventoryItemRepository;
    private final StockReservationRepository stockReservationRepository;

    @Transactional
    public InventoryItemResponse createInventoryItem(CreateInventoryItemRequest request) {
        if (inventoryItemRepository.existsByProductName(request.productName())) {
            throw new DuplicateResourceException(
                    "Product with name '" + request.productName() + "' already exists");
        }

        InventoryItem item = InventoryItem.builder()
                .productName(request.productName())
                .description(request.description())
                .quantityAvailable(request.quantityAvailable())
                .quantityReserved(0)
                .unitPrice(request.unitPrice())
                .active(true)
                .build();

        InventoryItem saved = inventoryItemRepository.save(item);
        log.info("Created inventory item: {}", saved.getId());
        return InventoryItemResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public InventoryItemResponse getInventoryItemById(Long id) {
        InventoryItem item = inventoryItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("InventoryItem", id));
        return InventoryItemResponse.from(item);
    }

    @Transactional(readOnly = true)
    public PagedResponse<InventoryItemResponse> getAllInventoryItems(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<InventoryItem> itemPage = inventoryItemRepository.findAll(pageRequest);
        return PagedResponse.of(itemPage, InventoryItemResponse::from);
    }

    @Transactional(readOnly = true)
    public PagedResponse<InventoryItemResponse> getAvailableItems(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("productName").ascending());
        Page<InventoryItem> itemPage = inventoryItemRepository.findAvailableItems(pageRequest);
        return PagedResponse.of(itemPage, InventoryItemResponse::from);
    }

    @Transactional
    public InventoryItemResponse updateInventoryItem(Long id, CreateInventoryItemRequest request) {
        InventoryItem item = inventoryItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("InventoryItem", id));

        if (!item.getProductName().equals(request.productName()) &&
                inventoryItemRepository.existsByProductName(request.productName())) {
            throw new DuplicateResourceException(
                    "Product with name '" + request.productName() + "' already exists");
        }

        item.setProductName(request.productName());
        item.setDescription(request.description());
        item.setQuantityAvailable(request.quantityAvailable());
        item.setUnitPrice(request.unitPrice());

        InventoryItem saved = inventoryItemRepository.save(item);
        log.info("Updated inventory item: {}", saved.getId());
        return InventoryItemResponse.from(saved);
    }

    @Transactional
    public void deleteInventoryItem(Long id) {
        InventoryItem item = inventoryItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("InventoryItem", id));

        List<StockReservation> activeReservations = stockReservationRepository
                .findByInventoryItemIdAndStatus(id, ReservationStatus.RESERVED);

        if (!activeReservations.isEmpty()) {
            throw new InvalidReservationStateException(
                    "Cannot delete item with active reservations");
        }

        inventoryItemRepository.delete(item);
        log.info("Deleted inventory item: {}", id);
    }

    @Transactional
    public InventoryItemResponse updateStock(Long id, Integer quantityChange) {
        InventoryItem item = inventoryItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("InventoryItem", id));

        int newQuantity = item.getQuantityAvailable() + quantityChange;
        if (newQuantity < item.getQuantityReserved()) {
            throw new InsufficientStockException(
                    "Cannot reduce stock below reserved quantity. Reserved: " + item.getQuantityReserved());
        }

        item.setQuantityAvailable(newQuantity);
        InventoryItem saved = inventoryItemRepository.save(item);
        log.info("Updated stock for item {}: {} -> {}", id, item.getQuantityAvailable(), newQuantity);
        return InventoryItemResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse checkAvailability(CheckAvailabilityRequest request) {
        List<Long> productIds = request.items().stream()
                .map(AvailabilityItemRequest::productId)
                .toList();

        Map<Long, InventoryItem> itemsMap = inventoryItemRepository.findActiveByIdIn(productIds)
                .stream()
                .collect(Collectors.toMap(InventoryItem::getId, Function.identity()));

        List<AvailabilityItemResponse> responses = new ArrayList<>();

        for (AvailabilityItemRequest itemRequest : request.items()) {
            InventoryItem item = itemsMap.get(itemRequest.productId());

            if (item == null) {
                responses.add(new AvailabilityItemResponse(
                        itemRequest.productId(),
                        "Unknown",
                        itemRequest.quantity(),
                        0,
                        false
                ));
            } else {
                int availableStock = item.getAvailableStock();
                boolean available = availableStock >= itemRequest.quantity();
                responses.add(new AvailabilityItemResponse(
                        item.getId(),
                        item.getProductName(),
                        itemRequest.quantity(),
                        availableStock,
                        available
                ));
            }
        }

        return AvailabilityResponse.from(responses);
    }

    @Transactional
    public ReservationResponse reserveStock(ReserveStockRequest request) {
        // Check if reservation already exists for this order
        if (stockReservationRepository.existsByOrderIdAndStatus(request.orderId(), ReservationStatus.RESERVED)) {
            throw new InvalidReservationStateException(
                    "Reservation already exists for order: " + request.orderId());
        }

        List<Long> productIds = request.items().stream()
                .map(AvailabilityItemRequest::productId)
                .toList();

        Map<Long, InventoryItem> itemsMap = inventoryItemRepository.findActiveByIdIn(productIds)
                .stream()
                .collect(Collectors.toMap(InventoryItem::getId, Function.identity()));

        // Validate all items have sufficient stock
        for (AvailabilityItemRequest itemRequest : request.items()) {
            InventoryItem item = itemsMap.get(itemRequest.productId());
            if (item == null) {
                throw new ResourceNotFoundException("InventoryItem", itemRequest.productId());
            }
            if (!item.hasAvailableStock(itemRequest.quantity())) {
                throw new InsufficientStockException(
                        item.getId(), itemRequest.quantity(), item.getAvailableStock());
            }
        }

        // Reserve stock for all items
        List<ReservationItemResponse> reservationResponses = new ArrayList<>();

        for (AvailabilityItemRequest itemRequest : request.items()) {
            InventoryItem item = itemsMap.get(itemRequest.productId());

            // Update reserved quantity
            item.setQuantityReserved(item.getQuantityReserved() + itemRequest.quantity());
            inventoryItemRepository.save(item);

            // Create reservation record
            StockReservation reservation = StockReservation.builder()
                    .orderId(request.orderId())
                    .inventoryItem(item)
                    .quantityReserved(itemRequest.quantity())
                    .status(ReservationStatus.RESERVED)
                    .build();

            StockReservation savedReservation = stockReservationRepository.save(reservation);
            reservationResponses.add(ReservationItemResponse.from(savedReservation));
        }

        log.info("Reserved stock for order {}: {} items", request.orderId(), reservationResponses.size());
        return ReservationResponse.success(request.orderId(), reservationResponses);
    }

    @Transactional
    public void confirmReservation(Long orderId) {
        List<StockReservation> reservations = stockReservationRepository
                .findByOrderIdAndStatus(orderId, ReservationStatus.RESERVED);

        if (reservations.isEmpty()) {
            throw new ResourceNotFoundException("No active reservations found for order: " + orderId);
        }

        for (StockReservation reservation : reservations) {
            InventoryItem item = reservation.getInventoryItem();

            // Deduct from both available and reserved quantities
            item.setQuantityAvailable(item.getQuantityAvailable() - reservation.getQuantityReserved());
            item.setQuantityReserved(item.getQuantityReserved() - reservation.getQuantityReserved());
            inventoryItemRepository.save(item);

            // Update reservation status
            reservation.setStatus(ReservationStatus.CONFIRMED);
            stockReservationRepository.save(reservation);
        }

        log.info("Confirmed reservation for order {}: {} items finalized", orderId, reservations.size());
    }

    @Transactional
    public void cancelReservation(Long orderId) {
        List<StockReservation> reservations = stockReservationRepository
                .findByOrderIdAndStatus(orderId, ReservationStatus.RESERVED);

        if (reservations.isEmpty()) {
            log.warn("No active reservations found to cancel for order: {}", orderId);
            return;
        }

        for (StockReservation reservation : reservations) {
            InventoryItem item = reservation.getInventoryItem();

            // Release reserved quantity
            item.setQuantityReserved(item.getQuantityReserved() - reservation.getQuantityReserved());
            inventoryItemRepository.save(item);

            // Update reservation status
            reservation.setStatus(ReservationStatus.CANCELLED);
            stockReservationRepository.save(reservation);
        }

        log.info("Cancelled reservation for order {}: {} items released", orderId, reservations.size());
    }
}
