package com.aditya.movieticketing.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Pure refund math. Given the refund-policy tiers (keyed by minimum hours before show, ordered
 * descending) and the hours remaining until the show, picks the applicable percentage and applies
 * it to the paid total.
 */
public final class RefundCalculator {

    public static final int MONEY_SCALE = 2;

    private RefundCalculator() {
    }

    /** A refund tier: at or above {@code minHoursBeforeShow} hours remaining, refund {@code percentage}. */
    public record Tier(int minHoursBeforeShow, BigDecimal percentage) {
    }

    /** Applicable percentage: the first tier (in the given desc order) whose threshold is met; else 0. */
    public static BigDecimal refundPercentage(List<Tier> tiersDescByHours, long hoursUntilShow) {
        for (Tier tier : tiersDescByHours) {
            if (hoursUntilShow >= tier.minHoursBeforeShow()) {
                return tier.percentage();
            }
        }
        return BigDecimal.ZERO;
    }

    public static BigDecimal refundAmount(BigDecimal total, BigDecimal percentage) {
        return total.multiply(percentage)
                .divide(BigDecimal.valueOf(100), MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
