package com.aditya.movieticketing.service;

import com.aditya.movieticketing.domain.DiscountCode;
import com.aditya.movieticketing.domain.SeatStatus;
import com.aditya.movieticketing.domain.Show;
import com.aditya.movieticketing.domain.ShowSeat;
import com.aditya.movieticketing.exception.InvalidDiscountException;
import com.aditya.movieticketing.exception.SeatUnavailableException;
import com.aditya.movieticketing.exception.ShowNotFoundException;
import com.aditya.movieticketing.repository.DiscountCodeRepository;
import com.aditya.movieticketing.repository.ShowRepository;
import com.aditya.movieticketing.repository.ShowSeatRepository;
import com.aditya.movieticketing.web.dto.HoldResponse;
import com.aditya.movieticketing.web.dto.PriceBreakdownResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class HoldService {

    static final Duration HOLD_DURATION = Duration.ofMinutes(10);

    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final DiscountCodeRepository discountCodeRepository;
    private final PricingService pricingService;

    public HoldService(ShowRepository showRepository,
                       ShowSeatRepository showSeatRepository,
                       DiscountCodeRepository discountCodeRepository,
                       PricingService pricingService) {
        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
        this.discountCodeRepository = discountCodeRepository;
        this.pricingService = pricingService;
    }

    /**
     * Holds the requested seats in a single transaction:
     * <ol>
     *   <li>lock the seat rows with SELECT ... FOR UPDATE ordered by seat id,</li>
     *   <li>verify all are available (or reclaimable expired holds),</li>
     *   <li>set them HELD with a shared hold token and a 10-minute expiry.</li>
     * </ol>
     * A concurrent hold on any overlapping seat blocks on step 1 until this transaction commits,
     * then fails at step 2 — this is what serializes bookings and prevents double allocation. If a
     * discount code is supplied it is validated here (and re-validated at confirm); the response
     * carries a non-binding price estimate.
     */
    @Transactional
    public HoldResponse hold(Long showId, List<Long> requestedSeatIds, String discountCode) {
        List<Long> seatIds = requestedSeatIds.stream().distinct().sorted().toList();

        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new ShowNotFoundException(showId));

        List<ShowSeat> locked = showSeatRepository.lockSeatsForHold(showId, seatIds);
        if (locked.size() != seatIds.size()) {
            throw new SeatUnavailableException("One or more requested seats do not exist for this show");
        }

        Instant now = Instant.now();
        for (ShowSeat showSeat : locked) {
            if (!isReclaimable(showSeat, now)) {
                throw new SeatUnavailableException(
                        "Seat " + showSeat.getSeat().getId() + " is not available");
            }
        }

        DiscountCode discount = resolveDiscount(discountCode);
        PriceBreakdown estimate = pricingService.price(show, locked, discount, now);

        UUID holdToken = UUID.randomUUID();
        Instant expiresAt = now.plus(HOLD_DURATION);
        for (ShowSeat showSeat : locked) {
            showSeat.setStatus(SeatStatus.HELD);
            showSeat.setHoldToken(holdToken);
            showSeat.setHoldExpiresAt(expiresAt);
        }

        return new HoldResponse(showId, seatIds, holdToken, expiresAt,
                new PriceBreakdownResponse(estimate.subtotal(), estimate.discount(),
                        estimate.tax(), estimate.total()));
    }

    private DiscountCode resolveDiscount(String discountCode) {
        if (!StringUtils.hasText(discountCode)) {
            return null;
        }
        return discountCodeRepository.findByCode(discountCode.trim())
                .orElseThrow(() -> new InvalidDiscountException("Unknown discount code " + discountCode));
    }

    /**
     * A seat can be held if it is AVAILABLE, or if it is HELD but its hold has already expired
     * (the scheduled sweep may not have run yet — we hold the row lock, so it is safe to reclaim).
     */
    private boolean isReclaimable(ShowSeat showSeat, Instant now) {
        if (showSeat.getStatus() == SeatStatus.AVAILABLE) {
            return true;
        }
        return showSeat.getStatus() == SeatStatus.HELD
                && showSeat.getHoldExpiresAt() != null
                && showSeat.getHoldExpiresAt().isBefore(now);
    }
}
