package com.tus.inventoryservice.repository;

import com.tus.inventoryservice.entity.ReservationStatus;
import com.tus.inventoryservice.entity.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    List<StockReservation> findByOrderId(Long orderId);

    List<StockReservation> findByOrderIdAndStatus(Long orderId, ReservationStatus status);

    boolean existsByOrderIdAndStatus(Long orderId, ReservationStatus status);

    List<StockReservation> findByInventoryItemIdAndStatus(Long inventoryItemId, ReservationStatus status);
}
