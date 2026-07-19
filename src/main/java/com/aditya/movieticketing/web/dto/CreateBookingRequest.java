package com.aditya.movieticketing.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateBookingRequest(
        @NotNull(message = "showId is required")
        Long showId,

        @NotNull(message = "holdToken is required")
        UUID holdToken,

        // SEAM (slice 5): replaced by the authenticated principal once HTTP Basic is wired.
        @NotNull(message = "userId is required")
        Long userId,

        // Optional discount code; re-validated and applied at confirm time.
        String discountCode
) {
}
