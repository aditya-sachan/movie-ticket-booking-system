package com.aditya.movieticketing.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record HoldRequest(
        @NotEmpty(message = "seatIds must not be empty")
        List<@NotNull Long> seatIds
) {
}
