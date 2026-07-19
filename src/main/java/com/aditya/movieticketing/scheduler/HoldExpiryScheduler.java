package com.aditya.movieticketing.scheduler;

import com.aditya.movieticketing.domain.SeatStatus;
import com.aditya.movieticketing.repository.ShowSeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Releases seats whose hold has expired, every 60 seconds. This is a backstop: holds already
 * reclaim their own expired rows on demand under lock, so this only mops up abandoned holds that
 * were never confirmed.
 */
@Component
public class HoldExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryScheduler.class);

    private final ShowSeatRepository showSeatRepository;

    public HoldExpiryScheduler(ShowSeatRepository showSeatRepository) {
        this.showSeatRepository = showSeatRepository;
    }

    @Scheduled(fixedDelayString = "${booking.hold-expiry.sweep-interval-ms:60000}")
    @Transactional
    public void releaseExpiredHolds() {
        int released = showSeatRepository.releaseExpiredHolds(
                Instant.now(), SeatStatus.AVAILABLE, SeatStatus.HELD);
        if (released > 0) {
            log.info("Released {} expired seat hold(s)", released);
        }
    }
}
