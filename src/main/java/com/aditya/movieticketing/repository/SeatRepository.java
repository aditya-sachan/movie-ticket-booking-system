package com.aditya.movieticketing.repository;

import com.aditya.movieticketing.domain.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByScreen_IdOrderById(Long screenId);
}
