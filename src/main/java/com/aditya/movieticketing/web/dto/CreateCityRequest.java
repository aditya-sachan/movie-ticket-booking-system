package com.aditya.movieticketing.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCityRequest(
        @NotBlank(message = "name is required") String name,
        String state
) {
}
