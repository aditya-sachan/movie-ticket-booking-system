package com.aditya.movieticketing.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateBookingRequest(
        @NotNull(message = "showId is required")
        Long showId,

        @NotNull(message = "holdToken is required")
        UUID holdToken,

        // Optional discount code; re-validated and applied at confirm time.
        String discountCode
) {
}
