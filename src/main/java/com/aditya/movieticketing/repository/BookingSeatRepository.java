package com.aditya.movieticketing.repository;

import com.aditya.movieticketing.domain.BookingSeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {

    List<BookingSeat> findByBooking_IdAndActiveTrue(Long bookingId);

    List<BookingSeat> findByBooking_Id(Long bookingId);
}
