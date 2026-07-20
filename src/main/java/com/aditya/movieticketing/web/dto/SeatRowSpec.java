package com.aditya.movieticketing.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record SeatRowSpec(
        @NotBlank(message = "rowLabel is required") String rowLabel,
        @Positive(message = "count must be > 0") int count,
        @NotBlank(message = "seatClassName is required") String seatClassName
) {
}
