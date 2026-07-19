package com.aditya.movieticketing.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Table-driven tests for the pure refund math, covering tier boundaries and the no-policy case.
 * Standard policy tiers: 48h→100%, 24h→50%, 2h→25%, 0h→0%.
 */
class RefundCalculatorTest {

    private static List<RefundCalculator.Tier> standardTiers() {
        return List.of(
                new RefundCalculator.Tier(48, new BigDecimal("100.00")),
                new RefundCalculator.Tier(24, new BigDecimal("50.00")),
                new RefundCalculator.Tier(2, new BigDecimal("25.00")),
                new RefundCalculator.Tier(0, new BigDecimal("0.00"))
        );
    }

    @ParameterizedTest
    @CsvSource({
            // hoursUntilShow, expectedPercentage
            "72, 100.00",
            "48, 100.00",   // exactly at 48h boundary
            "47, 50.00",    // just below 48h
            "24, 50.00",    // exactly at 24h boundary
            "23, 25.00",
            "2,  25.00",    // exactly at 2h boundary
            "1,  0.00",
            "0,  0.00"
    })
    @DisplayName("refundPercentage picks the highest tier whose threshold is met")
    void refundPercentage(long hoursUntilShow, BigDecimal expected) {
        assertThat(RefundCalculator.refundPercentage(standardTiers(), hoursUntilShow))
                .isEqualByComparingTo(expected);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("no tiers means no refund")
    void noTiers() {
        assertThat(RefundCalculator.refundPercentage(List.of(), 100)).isEqualByComparingTo("0");
    }

    @ParameterizedTest
    @CsvSource({
            // total, percentage, expectedAmount
            "500.00, 100.00, 500.00",
            "862.88, 50.00,  431.44",
            "333.33, 25.00,  83.33",   // rounding HALF_UP
            "500.00, 0.00,   0.00"
    })
    @DisplayName("refundAmount = total × percentage / 100, rounded HALF_UP")
    void refundAmount(BigDecimal total, BigDecimal percentage, BigDecimal expected) {
        assertThat(RefundCalculator.refundAmount(total, percentage)).isEqualByComparingTo(expected);
    }
}
