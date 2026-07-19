package com.aditya.movieticketing.service;

import java.math.BigDecimal;

public record RefundResult(
        BigDecimal percentage,
        BigDecimal amount
) {
}
