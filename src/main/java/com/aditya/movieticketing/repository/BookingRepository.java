package com.aditya.movieticketing.repository;

import com.aditya.movieticketing.domain.Booking;
import com.aditya.movieticketing.domain.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUser_IdOrderByCreatedAtDesc(Long userId);

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    /**
     * Confirmed bookings not yet reminded whose show starts within the reminder window (started
     * shows are excluded). Drives the reminder scheduler.
     */
    @Query("""
            select b from Booking b
            where b.status = :status
              and b.reminderEnqueued = false
              and b.show.startsAt >= :now
              and b.show.startsAt <= :until
            """)
    List<Booking> findBookingsNeedingReminder(@Param("status") BookingStatus status,
                                              @Param("now") Instant now,
                                              @Param("until") Instant until);
}
