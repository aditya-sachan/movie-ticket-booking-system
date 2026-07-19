package com.aditya.movieticketing.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record BookingSummaryResponse(
        Long bookingId,
        Long showId,
        String movieTitle,
        String status,
        List<Long> seatIds,
        BigDecimal totalAmount,
        Instant createdAt,
        Instant cancelledAt
) {
}
