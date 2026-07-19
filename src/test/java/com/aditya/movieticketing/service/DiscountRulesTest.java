package com.aditya.movieticketing.service;

import com.aditya.movieticketing.domain.DiscountCode;
import com.aditya.movieticketing.domain.DiscountType;
import com.aditya.movieticketing.exception.InvalidDiscountException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Boundary tests for discount-code validation.
 */
class DiscountRulesTest {

    private static final Instant NOW = Instant.parse("2026-07-19T12:00:00Z");

    private DiscountCode code(BigDecimal minOrder, Integer maxRedemptions, int timesRedeemed,
                              Instant from, Instant until, boolean active) {
        DiscountCode dc = new DiscountCode("WELCOME10", DiscountType.PERCENTAGE, new BigDecimal("10"),
                minOrder, maxRedemptions, from, until, active);
        dc.setTimesRedeemed(timesRedeemed);
        return dc;
    }

    @Test
    @DisplayName("valid code passes")
    void valid() {
        DiscountCode dc = code(new BigDecimal("200"), 100, 5,
                NOW.minus(1, ChronoUnit.DAYS), NOW.plus(1, ChronoUnit.DAYS), true);
        assertThatCode(() -> DiscountRules.validate(dc, NOW, new BigDecimal("500")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("inactive code is rejected")
    void inactive() {
        DiscountCode dc = code(new BigDecimal("0"), null, 0,
                NOW.minus(1, ChronoUnit.DAYS), NOW.plus(1, ChronoUnit.DAYS), false);
        assertThatThrownBy(() -> DiscountRules.validate(dc, NOW, new BigDecimal("500")))
                .isInstanceOf(InvalidDiscountException.class);
    }

    @Test
    @DisplayName("code before its validity window is rejected")
    void beforeWindow() {
        DiscountCode dc = code(new BigDecimal("0"), null, 0,
                NOW.plus(1, ChronoUnit.HOURS), NOW.plus(1, ChronoUnit.DAYS), true);
        assertThatThrownBy(() -> DiscountRules.validate(dc, NOW, new BigDecimal("500")))
                .isInstanceOf(InvalidDiscountException.class);
    }

    @Test
    @DisplayName("code after its validity window is rejected")
    void afterWindow() {
        DiscountCode dc = code(new BigDecimal("0"), null, 0,
                NOW.minus(2, ChronoUnit.DAYS), NOW.minus(1, ChronoUnit.HOURS), true);
        assertThatThrownBy(() -> DiscountRules.validate(dc, NOW, new BigDecimal("500")))
                .isInstanceOf(InvalidDiscountException.class);
    }

    @Test
    @DisplayName("code at its redemption limit is rejected")
    void maxRedemptionsReached() {
        DiscountCode dc = code(new BigDecimal("0"), 5, 5,
                NOW.minus(1, ChronoUnit.DAYS), NOW.plus(1, ChronoUnit.DAYS), true);
        assertThatThrownBy(() -> DiscountRules.validate(dc, NOW, new BigDecimal("500")))
                .isInstanceOf(InvalidDiscountException.class);
    }

    @Test
    @DisplayName("subtotal below minimum order value is rejected")
    void belowMinOrder() {
        DiscountCode dc = code(new BigDecimal("300"), null, 0,
                NOW.minus(1, ChronoUnit.DAYS), NOW.plus(1, ChronoUnit.DAYS), true);
        assertThatThrownBy(() -> DiscountRules.validate(dc, NOW, new BigDecimal("299.99")))
                .isInstanceOf(InvalidDiscountException.class);
    }

    @Test
    @DisplayName("subtotal exactly at minimum order value passes")
    void atMinOrder() {
        DiscountCode dc = code(new BigDecimal("300"), null, 0,
                NOW.minus(1, ChronoUnit.DAYS), NOW.plus(1, ChronoUnit.DAYS), true);
        assertThatCode(() -> DiscountRules.validate(dc, NOW, new BigDecimal("300.00")))
                .doesNotThrowAnyException();
    }
}
