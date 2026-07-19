package com.aditya.movieticketing.service;

import com.aditya.movieticketing.domain.AppUser;
import com.aditya.movieticketing.domain.Booking;
import com.aditya.movieticketing.exception.UserNotFoundException;
import com.aditya.movieticketing.repository.AppUserRepository;
import com.aditya.movieticketing.repository.BookingRepository;
import com.aditya.movieticketing.repository.BookingSeatRepository;
import com.aditya.movieticketing.web.dto.BookingSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Booking history for the authenticated customer. A customer only ever sees their own bookings
 * because the query is scoped to their user id (taken from the principal, not the request).
 */
@Service
public class BookingQueryService {

    private final AppUserRepository appUserRepository;
    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;

    public BookingQueryService(AppUserRepository appUserRepository,
                               BookingRepository bookingRepository,
                               BookingSeatRepository bookingSeatRepository) {
        this.appUserRepository = appUserRepository;
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
    }

    @Transactional(readOnly = true)
    public List<BookingSummaryResponse> myBookings(String username) {
        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));
        return bookingRepository.findByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toSummary)
                .toList();
    }

    private BookingSummaryResponse toSummary(Booking booking) {
        List<Long> seatIds = bookingSeatRepository.findByBooking_Id(booking.getId()).stream()
                .map(bs -> bs.getShowSeat().getSeat().getId())
                .sorted()
                .toList();
        return new BookingSummaryResponse(
                booking.getId(),
                booking.getShow().getId(),
                booking.getShow().getMovie().getTitle(),
                booking.getStatus().name(),
                seatIds,
                booking.getTotalAmount(),
                booking.getCreatedAt(),
                booking.getCancelledAt());
    }
}
