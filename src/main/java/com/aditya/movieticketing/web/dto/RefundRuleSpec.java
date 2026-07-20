package com.aditya.movieticketing.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record RefundRuleSpec(
        @NotNull @PositiveOrZero(message = "minHoursBeforeShow must be >= 0") Integer minHoursBeforeShow,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal refundPercentage
) {
}
