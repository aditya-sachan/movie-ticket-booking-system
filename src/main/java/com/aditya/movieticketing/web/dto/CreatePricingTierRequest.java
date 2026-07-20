package com.aditya.movieticketing.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreatePricingTierRequest(
        @NotBlank(message = "name is required") String name,
        @NotNull @Positive(message = "multiplier must be > 0") BigDecimal multiplier
) {
}
