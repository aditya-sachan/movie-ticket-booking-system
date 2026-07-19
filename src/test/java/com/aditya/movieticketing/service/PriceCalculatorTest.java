package com.aditya.movieticketing.service;

import com.aditya.movieticketing.domain.DiscountType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Table-driven tests for the pure pricing math, covering seat-class/tier multipliers, discount
 * types, flat-discount capping, rounding, and tax on the discounted subtotal.
 */
class PriceCalculatorTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    static Stream<Arguments> subtotalCases() {
        return Stream.of(
                // basePrice, tierMultiplier, seatClassMultipliers, expectedSubtotal
                Arguments.of(bd("250"), bd("1.0"), List.of(bd("1.0")), "250.00"),
                Arguments.of(bd("250"), bd("1.3"), List.of(bd("1.0")), "325.00"),
                Arguments.of(bd("250"), bd("1.3"), List.of(bd("1.5")), "487.50"),
                // mixed classes: one premium + one regular at weekend tier
                Arguments.of(bd("250"), bd("1.3"), List.of(bd("1.5"), bd("1.0")), "812.50"),
                Arguments.of(bd("200"), bd("1.2"), List.of(bd("1.0"), bd("1.0"), bd("1.5")), "840.00")
        );
    }

    @ParameterizedTest
    @MethodSource("subtotalCases")
    @DisplayName("subtotal = Σ base × seatClassMultiplier × tierMultiplier")
    void subtotal(BigDecimal basePrice, BigDecimal tier, List<BigDecimal> scms, String expected) {
        assertThat(PriceCalculator.subtotal(basePrice, tier, scms)).isEqualByComparingTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            // type, value, subtotal, expectedDiscount
            "PERCENTAGE, 10, 812.50, 81.25",
            "PERCENTAGE, 100, 500.00, 500.00",   // 100% caps at subtotal
            "PERCENTAGE, 33, 100.00, 33.00",       // rounding HALF_UP
            "FLAT, 50, 812.50, 50.00",
            "FLAT, 900, 812.50, 812.50",           // flat exceeding subtotal is capped
            "FLAT, 0, 500.00, 0.00"
    })
    @DisplayName("discountAmount handles percentage, flat, capping, and rounding")
    void discountAmount(DiscountType type, BigDecimal value, BigDecimal subtotal, String expected) {
        assertThat(PriceCalculator.discountAmount(type, value, subtotal)).isEqualByComparingTo(expected);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("null discount type yields zero discount")
    void discountAmountNull() {
        assertThat(PriceCalculator.discountAmount(null, null, bd("500"))).isEqualByComparingTo("0.00");
    }

    static Stream<Arguments> breakdownCases() {
        return Stream.of(
                // subtotal, discount, taxRate, expSubtotal, expDiscount, expTax, expTotal
                Arguments.of(bd("500.00"), bd("0.00"), bd("0.18"), "500.00", "0.00", "90.00", "590.00"),
                Arguments.of(bd("812.50"), bd("81.25"), bd("0.18"), "812.50", "81.25", "131.63", "862.88"),
                Arguments.of(bd("500.00"), bd("0.00"), bd("0.00"), "500.00", "0.00", "0.00", "500.00"),
                // discount larger than subtotal is capped so total never goes negative
                Arguments.of(bd("100.00"), bd("150.00"), bd("0.18"), "100.00", "100.00", "0.00", "0.00")
        );
    }

    @ParameterizedTest
    @MethodSource("breakdownCases")
    @DisplayName("breakdown taxes the discounted subtotal and never goes negative")
    void breakdown(BigDecimal subtotal, BigDecimal discount, BigDecimal taxRate,
                   String expSub, String expDisc, String expTax, String expTotal) {
        PriceBreakdown b = PriceCalculator.breakdown(subtotal, discount, taxRate);
        assertThat(b.subtotal()).isEqualByComparingTo(expSub);
        assertThat(b.discount()).isEqualByComparingTo(expDisc);
        assertThat(b.tax()).isEqualByComparingTo(expTax);
        assertThat(b.total()).isEqualByComparingTo(expTotal);
    }
}
