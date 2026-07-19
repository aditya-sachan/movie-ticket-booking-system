package com.aditya.movieticketing.service;

import com.aditya.movieticketing.domain.Booking;
import com.aditya.movieticketing.domain.RefundPolicy;
import com.aditya.movieticketing.domain.RefundRule;
import com.aditya.movieticketing.domain.Show;
import com.aditya.movieticketing.repository.RefundRuleRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Computes a cancellation refund from the show's configured {@link RefundPolicy}. Rules are keyed
 * by "minimum hours before show"; the applicable rule is the one with the largest threshold that
 * is still at or below the actual hours remaining. If the show has no policy, the refund is zero.
 */
@Service
public class RefundService {

    private static final int MONEY_SCALE = 2;

    private final RefundRuleRepository refundRuleRepository;

    public RefundService(RefundRuleRepository refundRuleRepository) {
        this.refundRuleRepository = refundRuleRepository;
    }

    public RefundResult computeRefund(Booking booking, Instant now) {
        Show show = booking.getShow();
        BigDecimal percentage = BigDecimal.ZERO;

        RefundPolicy policy = show.getRefundPolicy();
        if (policy != null) {
            long hoursUntilShow = Math.max(0, Duration.between(now, show.getStartsAt()).toHours());
            List<RefundRule> rules =
                    refundRuleRepository.findByRefundPolicy_IdOrderByMinHoursBeforeShowDesc(policy.getId());
            for (RefundRule rule : rules) {
                if (hoursUntilShow >= rule.getMinHoursBeforeShow()) {
                    percentage = rule.getRefundPercentage();
                    break;
                }
            }
        }

        BigDecimal amount = booking.getTotalAmount()
                .multiply(percentage)
                .divide(BigDecimal.valueOf(100), MONEY_SCALE, RoundingMode.HALF_UP);
        return new RefundResult(percentage, amount);
    }
}
