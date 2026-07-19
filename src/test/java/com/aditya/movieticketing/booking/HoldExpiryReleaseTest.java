package com.aditya.movieticketing.booking;

import com.aditya.movieticketing.AbstractIntegrationTest;
import com.aditya.movieticketing.domain.SeatStatus;
import com.aditya.movieticketing.domain.ShowSeat;
import com.aditya.movieticketing.repository.ShowRepository;
import com.aditya.movieticketing.repository.ShowSeatRepository;
import com.aditya.movieticketing.scheduler.HoldExpiryScheduler;
import com.aditya.movieticketing.service.HoldService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HoldExpiryReleaseTest extends AbstractIntegrationTest {

    @Autowired
    private HoldService holdService;
    @Autowired
    private ShowRepository showRepository;
    @Autowired
    private ShowSeatRepository showSeatRepository;
    @Autowired
    private HoldExpiryScheduler holdExpiryScheduler;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("the scheduled sweep releases a hold whose expiry has passed")
    void expiredHoldIsReleased() {
        Long showId = showRepository.findAll().stream().findFirst().orElseThrow().getId();
        ShowSeat target = showSeatRepository.findByShowWithSeat(showId).get(5);
        Long seatId = target.getSeat().getId();
        Long showSeatId = target.getId();

        holdService.hold(showId, List.of(seatId), null);
        assertThat(reloadStatus(showSeatId)).isEqualTo(SeatStatus.HELD);

        // Backdate the hold so it is expired, then run the sweep.
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> showSeatRepository.findById(showSeatId)
                .ifPresent(ss -> ss.setHoldExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES))));

        holdExpiryScheduler.releaseExpiredHolds();

        ShowSeat released = showSeatRepository.findById(showSeatId).orElseThrow();
        assertThat(released.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(released.getHoldToken()).isNull();
        assertThat(released.getHoldExpiresAt()).isNull();
    }

    private SeatStatus reloadStatus(Long showSeatId) {
        return showSeatRepository.findById(showSeatId).orElseThrow().getStatus();
    }
}
