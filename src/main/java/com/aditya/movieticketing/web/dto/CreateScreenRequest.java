package com.aditya.movieticketing.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateScreenRequest(
        @NotNull(message = "theaterId is required") Long theaterId,
        @NotBlank(message = "name is required") String name
) {
}
