package com.aditya.movieticketing.service;

import com.aditya.movieticketing.domain.DiscountType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Pure pricing math — no Spring, no persistence, fully unit-testable.
 *
 * <p>Formula: {@code final = (base_price × seat_class_multiplier × tier_multiplier) − discount + taxes}.
 * The per-seat term {@code base × seatClassMultiplier × tierMultiplier} is summed across seats to
 * form the subtotal; discount is then subtracted and tax applied on the discounted subtotal.
 */
public final class PriceCalculator {

    public static final int MONEY_SCALE = 2;

    private PriceCalculator() {
    }

    /** subtotal = Σ over seats of (basePrice × seatClassMultiplier × tierMultiplier). */
    public static BigDecimal subtotal(BigDecimal basePrice,
                                      BigDecimal tierMultiplier,
                                      List<BigDecimal> seatClassMultipliers) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal seatClassMultiplier : seatClassMultipliers) {
            sum = sum.add(basePrice.multiply(seatClassMultiplier).multiply(tierMultiplier));
        }
        return scale(sum);
    }

    /** Discount amount for a code applied to a subtotal. Never exceeds the subtotal. */
    public static BigDecimal discountAmount(DiscountType type, BigDecimal value, BigDecimal subtotal) {
        if (type == null || value == null) {
            return scale(BigDecimal.ZERO);
        }
        BigDecimal amount = switch (type) {
            case PERCENTAGE -> subtotal.multiply(value).divide(BigDecimal.valueOf(100), MONEY_SCALE, RoundingMode.HALF_UP);
            case FLAT -> value;
        };
        if (amount.compareTo(subtotal) > 0) {
            amount = subtotal;
        }
        return scale(amount);
    }

    /**
     * Assembles the final breakdown from a subtotal, a discount amount, and a tax rate.
     * Tax is charged on the discounted subtotal.
     */
    public static PriceBreakdown breakdown(BigDecimal subtotal, BigDecimal discount, BigDecimal taxRate) {
        BigDecimal cappedDiscount = discount.min(subtotal);
        BigDecimal taxable = subtotal.subtract(cappedDiscount);
        BigDecimal tax = scale(taxable.multiply(taxRate));
        BigDecimal total = scale(taxable.add(tax));
        return new PriceBreakdown(scale(subtotal), scale(cappedDiscount), tax, total);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
