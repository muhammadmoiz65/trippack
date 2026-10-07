package com.trippack.trip;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TripRepository extends JpaRepository<Trip, Long> {

    /** Trips sorted by start date, the next trip first. */
    List<Trip> findAllByOrderByStartDateAsc();
}
