package com.aditya.movieticketing.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HoldResponse(
        Long showId,
        List<Long> seatIds,
        UUID holdToken,
        Instant expiresAt
) {
}
