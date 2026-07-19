package com.aditya.movieticketing.service;

import com.aditya.movieticketing.domain.Show;
import com.aditya.movieticketing.domain.ShowSeat;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Pricing seam.
 *
 * <p><b>Slice 2 (now):</b> uses {@code base_price} only — {@code subtotal = base_price × seatCount},
 * with zero discount and tax.
 *
 * <p><b>Slice 3 (planned):</b> replace the body of {@link #priceBooking} with the full formula
 * <pre>final = (base_price × seat_class_multiplier × tier_multiplier) − discount + taxes</pre>
 * and add discount-code validation (validated at hold time, re-validated here at confirm time).
 * The method signature and the {@link PriceBreakdown} return type are the stable seam, so callers
 * (BookingService) do not change when pricing gets richer.
 */
@Service
public class PricingService {

    private static final int MONEY_SCALE = 2;

    public PriceBreakdown priceBooking(Show show, List<ShowSeat> seats) {
        BigDecimal subtotal = show.getBasePrice()
                .multiply(BigDecimal.valueOf(seats.size()))
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal discount = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal tax = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.subtract(discount).add(tax).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        return new PriceBreakdown(subtotal, discount, tax, total);
    }
}
