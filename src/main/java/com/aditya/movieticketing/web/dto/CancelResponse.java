package com.aditya.movieticketing.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CancelResponse(
        Long bookingId,
        String status,
        BigDecimal refundAmount,
        BigDecimal refundPercentage,
        Instant cancelledAt
) {
}
