package com.aditya.movieticketing.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateShowRequest(
        @NotNull(message = "movieId is required") Long movieId,
        @NotNull(message = "screenId is required") Long screenId,
        @NotNull(message = "pricingTierId is required") Long pricingTierId,
        Long refundPolicyId,
        @NotNull(message = "startsAt is required") Instant startsAt,
        @NotNull(message = "endsAt is required") Instant endsAt,
        @NotNull @PositiveOrZero(message = "basePrice must be >= 0") BigDecimal basePrice
) {
}
