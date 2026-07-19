package com.aditya.movieticketing.repository;

import com.aditya.movieticketing.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findFirstByBooking_IdOrderByIdDesc(Long bookingId);
}
