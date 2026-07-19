package com.aditya.movieticketing.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record BookingResponse(
        Long bookingId,
        Long showId,
        Long userId,
        String status,
        List<Long> seatIds,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal tax,
        BigDecimal total,
        Instant createdAt
) {
}
