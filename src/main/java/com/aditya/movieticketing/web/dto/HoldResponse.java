package com.aditya.movieticketing.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HoldResponse(
        Long showId,
        List<Long> seatIds,
        UUID holdToken,
        Instant expiresAt,
        // Non-binding price preview for the held seats (re-computed at confirm).
        PriceBreakdownResponse priceEstimate
) {
}
