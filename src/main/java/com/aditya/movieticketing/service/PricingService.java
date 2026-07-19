package com.aditya.movieticketing.service;

import com.aditya.movieticketing.domain.DiscountCode;
import com.aditya.movieticketing.domain.PricingTier;
import com.aditya.movieticketing.domain.Show;
import com.aditya.movieticketing.domain.ShowSeat;
import com.aditya.movieticketing.repository.PricingTierRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * Resolves the multipliers, discount, and tax rate for a set of seats and delegates the arithmetic
 * to the pure {@link PriceCalculator}.
 *
 * <p>Tier resolution follows "WEEKEND tier by show day": if the show starts on a Saturday or Sunday
 * (in {@link #PRICING_ZONE}) the WEEKEND tier multiplier applies; otherwise the show's configured
 * pricing tier (REGULAR/PREMIUM) applies. Seat-class multipliers are read per seat, so a mix of
 * regular and premium seats prices correctly. Tax is a configurable flat rate on the discounted
 * subtotal ({@code pricing.tax-rate}, default 0.18).
 */
@Service
public class PricingService {

    /** Zone used to decide whether a show falls on a weekend (Indian theaters). */
    static final ZoneId PRICING_ZONE = ZoneId.of("Asia/Kolkata");
    static final String WEEKEND_TIER = "WEEKEND";

    private final PricingTierRepository pricingTierRepository;
    private final BigDecimal taxRate;

    public PricingService(PricingTierRepository pricingTierRepository,
                          @Value("${pricing.tax-rate:0.18}") BigDecimal taxRate) {
        this.pricingTierRepository = pricingTierRepository;
        this.taxRate = taxRate;
    }

    /**
     * Prices a set of held seats. If {@code discount} is non-null it is validated (active, within
     * window, under redemption limit, meets minimum order value) and applied; an invalid code
     * raises {@link com.aditya.movieticketing.exception.InvalidDiscountException}.
     */
    public PriceBreakdown price(Show show, List<ShowSeat> seats, DiscountCode discount, Instant now) {
        BigDecimal tierMultiplier = resolveTierMultiplier(show);
        List<BigDecimal> seatClassMultipliers = seats.stream()
                .map(seat -> seat.getSeat().getSeatClass().getMultiplier())
                .toList();

        BigDecimal subtotal = PriceCalculator.subtotal(show.getBasePrice(), tierMultiplier, seatClassMultipliers);

        BigDecimal discountAmount = BigDecimal.ZERO;
        if (discount != null) {
            DiscountRules.validate(discount, now, subtotal);
            discountAmount = PriceCalculator.discountAmount(discount.getDiscountType(), discount.getValue(), subtotal);
        }

        return PriceCalculator.breakdown(subtotal, discountAmount, taxRate);
    }

    private BigDecimal resolveTierMultiplier(Show show) {
        DayOfWeek day = show.getStartsAt().atZone(PRICING_ZONE).getDayOfWeek();
        boolean weekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
        if (weekend) {
            return pricingTierRepository.findByName(WEEKEND_TIER)
                    .map(PricingTier::getMultiplier)
                    .orElseGet(() -> show.getPricingTier().getMultiplier());
        }
        return show.getPricingTier().getMultiplier();
    }
}
