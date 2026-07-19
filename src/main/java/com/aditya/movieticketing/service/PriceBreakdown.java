package com.aditya.movieticketing.service;

import java.math.BigDecimal;

/**
 * Result of pricing a set of seats: subtotal, discount, tax, and the final total.
 */
public record PriceBreakdown(
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal tax,
        BigDecimal total
) {
}
