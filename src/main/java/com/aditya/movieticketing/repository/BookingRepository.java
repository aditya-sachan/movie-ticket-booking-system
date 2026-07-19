package com.aditya.movieticketing.repository;

import com.aditya.movieticketing.domain.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUser_IdOrderByCreatedAtDesc(Long userId);
}
