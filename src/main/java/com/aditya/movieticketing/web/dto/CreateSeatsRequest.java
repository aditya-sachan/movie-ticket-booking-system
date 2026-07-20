package com.aditya.movieticketing.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateSeatsRequest(
        @NotEmpty(message = "rows must not be empty") List<@Valid SeatRowSpec> rows
) {
}
