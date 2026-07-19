package com.aditya.movieticketing.web.dto;

import jakarta.validation.constraints.NotNull;

public record CancelBookingRequest(
        // SEAM (slice 5): replaced by the authenticated principal once HTTP Basic is wired.
        @NotNull(message = "userId is required")
        Long userId
) {
}
