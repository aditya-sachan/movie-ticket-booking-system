package com.aditya.movieticketing.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

public record CreateTheaterRequest(
        @NotNull(message = "cityId is required") Long cityId,
        @NotBlank(message = "name is required") String name,
        String address
) {
}
