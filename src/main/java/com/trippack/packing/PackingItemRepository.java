package com.trippack.packing;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface PackingItemRepository extends JpaRepository<PackingItem, Long> {

    List<PackingItem> findByTripId(Long tripId);

    Optional<PackingItem> findByIdAndTripId(Long id, Long tripId);

    @Transactional
    void deleteByTripIdAndCustomFalse(Long tripId);

    @Transactional
    void deleteByTripId(Long tripId);
}
