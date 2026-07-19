package com.aditya.movieticketing.service;

import com.aditya.movieticketing.domain.Booking;
import com.aditya.movieticketing.domain.RefundPolicy;
import com.aditya.movieticketing.domain.Show;
import com.aditya.movieticketing.repository.RefundRuleRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Loads the show's refund-policy rules and delegates the arithmetic to the pure
 * {@link RefundCalculator}. If the show has no policy, the refund is zero.
 */
@Service
public class RefundService {

    private final RefundRuleRepository refundRuleRepository;

    public RefundService(RefundRuleRepository refundRuleRepository) {
        this.refundRuleRepository = refundRuleRepository;
    }

    public RefundResult computeRefund(Booking booking, Instant now) {
        Show show = booking.getShow();
        RefundPolicy policy = show.getRefundPolicy();
        if (policy == null) {
            return new RefundResult(BigDecimal.ZERO, BigDecimal.ZERO
                    .setScale(RefundCalculator.MONEY_SCALE, java.math.RoundingMode.HALF_UP));
        }

        long hoursUntilShow = Math.max(0, Duration.between(now, show.getStartsAt()).toHours());
        List<RefundCalculator.Tier> tiers = refundRuleRepository
                .findByRefundPolicy_IdOrderByMinHoursBeforeShowDesc(policy.getId())
                .stream()
                .map(rule -> new RefundCalculator.Tier(rule.getMinHoursBeforeShow(), rule.getRefundPercentage()))
                .toList();

        BigDecimal percentage = RefundCalculator.refundPercentage(tiers, hoursUntilShow);
        BigDecimal amount = RefundCalculator.refundAmount(booking.getTotalAmount(), percentage);
        return new RefundResult(percentage, amount);
    }
}
