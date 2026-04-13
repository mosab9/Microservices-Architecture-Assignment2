package com.tus.inventoryservice.repository;

import com.tus.inventoryservice.entity.InventoryItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    Optional<InventoryItem> findByProductName(String productName);

    boolean existsByProductName(String productName);

    Page<InventoryItem> findByActiveTrue(Pageable pageable);

    List<InventoryItem> findByIdIn(List<Long> ids);

    @Query("SELECT i FROM InventoryItem i WHERE i.active = true AND (i.quantityAvailable - i.quantityReserved) > 0")
    Page<InventoryItem> findAvailableItems(Pageable pageable);

    @Query("SELECT i FROM InventoryItem i WHERE i.id IN :ids AND i.active = true")
    List<InventoryItem> findActiveByIdIn(@Param("ids") List<Long> ids);
}
