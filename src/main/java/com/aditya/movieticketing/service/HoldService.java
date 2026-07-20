package com.aditya.movieticketing.service;

import com.aditya.movieticketing.domain.DiscountCode;
import com.aditya.movieticketing.domain.SeatStatus;
import com.aditya.movieticketing.domain.Show;
import com.aditya.movieticketing.domain.ShowSeat;
import com.aditya.movieticketing.exception.InvalidDiscountException;
import com.aditya.movieticketing.exception.InvalidSeatSelectionException;
import com.aditya.movieticketing.exception.SeatUnavailableException;
import com.aditya.movieticketing.exception.ShowNotFoundException;
import com.aditya.movieticketing.repository.DiscountCodeRepository;
import com.aditya.movieticketing.repository.ShowRepository;
import com.aditya.movieticketing.repository.ShowSeatRepository;
import com.aditya.movieticketing.web.dto.HoldResponse;
import com.aditya.movieticketing.web.dto.PriceBreakdownResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class HoldService {

    static final Duration HOLD_DURATION = Duration.ofMinutes(10);

    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final DiscountCodeRepository discountCodeRepository;
    private final PricingService pricingService;
    private final int maxSeatsPerBooking;

    public HoldService(ShowRepository showRepository,
                       ShowSeatRepository showSeatRepository,
                       DiscountCodeRepository discountCodeRepository,
                       PricingService pricingService,
                       @Value("${booking.max-seats-per-booking:10}") int maxSeatsPerBooking) {
        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
        this.discountCodeRepository = discountCodeRepository;
        this.pricingService = pricingService;
        this.maxSeatsPerBooking = maxSeatsPerBooking;
    }

    /**
     * Holds the requested seats in a single transaction:
     * <ol>
     *   <li>lock the seat rows with SELECT ... FOR UPDATE ordered by seat id,</li>
     *   <li>verify all are available (or reclaimable expired holds),</li>
     *   <li>enforce seat-selection rules (max per booking, no orphaned seat),</li>
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

        validateSeatSelection(showId, locked, now);

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

    /**
     * Enforces the max-seats cap and the no-orphan-seat rule. For every row touched by the hold, the
     * row's post-hold occupancy (existing bookings/live holds plus the seats being held now) must
     * not leave an available seat isolated between two occupied seats.
     */
    private void validateSeatSelection(Long showId, List<ShowSeat> locked, Instant now) {
        SeatSelectionRules.validateMaxSeats(locked.size(), maxSeatsPerBooking);

        Set<Long> requestedShowSeatIds = new HashSet<>();
        Set<String> affectedRows = new HashSet<>();
        for (ShowSeat showSeat : locked) {
            requestedShowSeatIds.add(showSeat.getId());
            affectedRows.add(showSeat.getSeat().getRowLabel());
        }

        Map<String, Map<Integer, Boolean>> occupancyByRow = new HashMap<>();
        for (ShowSeat showSeat : showSeatRepository.findByShowWithSeat(showId)) {
            String row = showSeat.getSeat().getRowLabel();
            if (!affectedRows.contains(row)) {
                continue;
            }
            boolean occupied = requestedShowSeatIds.contains(showSeat.getId()) || isOccupied(showSeat, now);
            occupancyByRow.computeIfAbsent(row, key -> new HashMap<>())
                    .put(showSeat.getSeat().getSeatNumber(), occupied);
        }

        for (Map.Entry<String, Map<Integer, Boolean>> entry : occupancyByRow.entrySet()) {
            if (SeatSelectionRules.hasOrphanSeat(entry.getValue())) {
                throw new InvalidSeatSelectionException(
                        "Selection would leave an isolated single seat in row " + entry.getKey());
            }
        }
    }

    private boolean isOccupied(ShowSeat showSeat, Instant now) {
        if (showSeat.getStatus() == SeatStatus.BOOKED) {
            return true;
        }
        return showSeat.getStatus() == SeatStatus.HELD
                && showSeat.getHoldExpiresAt() != null
                && !showSeat.getHoldExpiresAt().isBefore(now);
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
