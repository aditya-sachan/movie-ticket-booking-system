package com.aditya.movieticketing.concurrency;

import com.aditya.movieticketing.AbstractIntegrationTest;
import com.aditya.movieticketing.domain.SeatStatus;
import com.aditya.movieticketing.domain.ShowSeat;
import com.aditya.movieticketing.exception.SeatUnavailableException;
import com.aditya.movieticketing.repository.BookingSeatRepository;
import com.aditya.movieticketing.repository.ShowRepository;
import com.aditya.movieticketing.repository.ShowSeatRepository;
import com.aditya.movieticketing.service.BookingService;
import com.aditya.movieticketing.service.HoldService;
import com.aditya.movieticketing.web.dto.CreateBookingRequest;
import com.aditya.movieticketing.web.dto.HoldResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The most important test in the project: 50 threads race to book the SAME seat on the SAME show.
 * Backed by a real PostgreSQL 16 (Testcontainers) because H2 does not reproduce SELECT ... FOR
 * UPDATE row locking.
 */
class SeatBookingConcurrencyTest extends AbstractIntegrationTest {

    private static final int THREADS = 50;

    @Autowired
    private HoldService holdService;
    @Autowired
    private BookingService bookingService;
    @Autowired
    private ShowRepository showRepository;
    @Autowired
    private ShowSeatRepository showSeatRepository;
    @Autowired
    private BookingSeatRepository bookingSeatRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("50 threads, one seat, row lock ON: exactly one booking wins, 49 get SeatUnavailable")
    void exactlyOneBookingWins_withRowLock() throws Exception {
        Long showId = anyShowId();
        ShowSeat target = seatOfShow(showId, 0);
        Long seatId = target.getSeat().getId();
        Long showSeatId = target.getId();

        AtomicInteger booked = new AtomicInteger();
        AtomicInteger seatUnavailable = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> unexpected = new ConcurrentLinkedQueue<>();

        runConcurrently(() -> {
            try {
                HoldResponse hold = holdService.hold(showId, List.of(seatId), null);
                bookingService.confirm(new CreateBookingRequest(showId, hold.holdToken(), null), "alice", null);
                booked.incrementAndGet();
            } catch (SeatUnavailableException expected) {
                seatUnavailable.incrementAndGet();
            } catch (Throwable other) {
                unexpected.add(other);
            }
        });

        assertThat(unexpected).as("no unexpected exceptions").isEmpty();
        assertThat(booked.get()).as("exactly one booking succeeds").isEqualTo(1);
        assertThat(seatUnavailable.get()).as("the other 49 get SeatUnavailable").isEqualTo(THREADS - 1);
        assertThat(bookingSeatRepository.countByShowSeat_IdAndActiveTrue(showSeatId))
                .as("exactly one active booking_seat for the contested seat").isEqualTo(1);
    }

    @Test
    @DisplayName("50 threads, one seat, row lock REMOVED: multiple pass the availability check (race), "
            + "but the DB unique-index backstop still prevents double-booking")
    void rowLockRemoved_racesButBackstopHolds() throws Exception {
        Long showId = anyShowId();
        ShowSeat target = seatOfShow(showId, 1); // a different seat than the locked test
        Long seatId = target.getSeat().getId();
        Long showSeatId = target.getId();

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        UUID sharedToken = UUID.randomUUID();
        AtomicInteger passedAvailabilityCheck = new AtomicInteger();
        AtomicInteger booked = new AtomicInteger();

        runConcurrently(() -> {
            try {
                // UNSAFE hold: same selection but WITHOUT the pessimistic lock, with a widened
                // check-then-act window so the race is deterministic.
                tx.executeWithoutResult(status -> {
                    ShowSeat ss = showSeatRepository
                            .findSeatsForHoldWithoutLock(showId, List.of(seatId)).get(0);
                    if (ss.getStatus() != SeatStatus.AVAILABLE) {
                        throw new SeatUnavailableException("already taken");
                    }
                    passedAvailabilityCheck.incrementAndGet();
                    sleepQuietly(50);
                    ss.setStatus(SeatStatus.HELD);
                    ss.setHoldToken(sharedToken);
                    ss.setHoldExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
                });
                bookingService.confirm(new CreateBookingRequest(showId, sharedToken, null), "alice", null);
                booked.incrementAndGet();
            } catch (Throwable expectedUnderRace) {
                // DataIntegrityViolation / HoldExpired / SeatUnavailable are all acceptable here.
            }
        });

        assertThat(passedAvailabilityCheck.get())
                .as("without the row lock, multiple threads see the seat as AVAILABLE (the race)")
                .isGreaterThan(1);
        assertThat(booked.get())
                .as("the DB unique-index backstop still allows at most one booking")
                .isEqualTo(1);
        assertThat(bookingSeatRepository.countByShowSeat_IdAndActiveTrue(showSeatId))
                .as("no double-allocation at the DB level").isEqualTo(1);
    }

    // --- helpers -------------------------------------------------------------

    private void runConcurrently(Runnable action) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    action.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await(10, TimeUnit.SECONDS);
        start.countDown(); // release all threads at once
        done.await(60, TimeUnit.SECONDS);
        pool.shutdownNow();
    }

    private Long anyShowId() {
        return showRepository.findAll().stream().findFirst().orElseThrow().getId();
    }

    private ShowSeat seatOfShow(Long showId, int index) {
        return showSeatRepository.findByShowWithSeat(showId).get(index);
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
