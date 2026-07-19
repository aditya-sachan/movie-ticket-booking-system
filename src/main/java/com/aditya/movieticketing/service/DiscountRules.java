package com.aditya.movieticketing.service;

import com.aditya.movieticketing.domain.DiscountCode;
import com.aditya.movieticketing.exception.InvalidDiscountException;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Pure discount-code validation. Throws {@link InvalidDiscountException} with a specific reason if
 * the code cannot be applied. Used at hold time and re-checked at confirm time.
 */
public final class DiscountRules {

    private DiscountRules() {
    }

    public static void validate(DiscountCode code, Instant now, BigDecimal subtotal) {
        if (!code.isActive()) {
            throw new InvalidDiscountException("Discount code " + code.getCode() + " is not active");
        }
        if (now.isBefore(code.getValidFrom()) || now.isAfter(code.getValidUntil())) {
            throw new InvalidDiscountException("Discount code " + code.getCode() + " is outside its validity window");
        }
        if (code.getMaxRedemptions() != null && code.getTimesRedeemed() >= code.getMaxRedemptions()) {
            throw new InvalidDiscountException("Discount code " + code.getCode() + " has reached its redemption limit");
        }
        if (subtotal.compareTo(code.getMinOrderValue()) < 0) {
            throw new InvalidDiscountException("Order subtotal " + subtotal
                    + " is below the minimum " + code.getMinOrderValue() + " for code " + code.getCode());
        }
    }
}
