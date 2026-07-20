package com.aditya.movieticketing.booking;

import com.aditya.movieticketing.AbstractIntegrationTest;
import com.aditya.movieticketing.domain.NotificationType;
import com.aditya.movieticketing.domain.ShowSeat;
import com.aditya.movieticketing.repository.BookingRepository;
import com.aditya.movieticketing.repository.MovieRepository;
import com.aditya.movieticketing.repository.NotificationOutboxRepository;
import com.aditya.movieticketing.repository.PricingTierRepository;
import com.aditya.movieticketing.repository.ScreenRepository;
import com.aditya.movieticketing.repository.ShowSeatRepository;
import com.aditya.movieticketing.scheduler.ReminderScheduler;
import com.aditya.movieticketing.service.AdminService;
import com.aditya.movieticketing.service.BookingService;
import com.aditya.movieticketing.service.HoldService;
import com.aditya.movieticketing.web.dto.BookingResponse;
import com.aditya.movieticketing.web.dto.CreateBookingRequest;
import com.aditya.movieticketing.web.dto.CreateShowRequest;
import com.aditya.movieticketing.web.dto.HoldResponse;
import com.aditya.movieticketing.web.dto.ShowSummaryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A confirmed booking for a show starting within the reminder window gets exactly one REMINDER
 * outbox row, and re-running the scheduler does not enqueue a second one.
 */
class ReminderNotificationTest extends AbstractIntegrationTest {

    @Autowired private AdminService adminService;
    @Autowired private HoldService holdService;
    @Autowired private BookingService bookingService;
    @Autowired private ReminderScheduler reminderScheduler;
    @Autowired private MovieRepository movieRepository;
    @Autowired private ScreenRepository screenRepository;
    @Autowired private PricingTierRepository pricingTierRepository;
    @Autowired private ShowSeatRepository showSeatRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private NotificationOutboxRepository outboxRepository;

    @Test
    @DisplayName("a booking on a soon-starting show is reminded exactly once")
    void remindsConfirmedBookingOnce() {
        // A show starting in 2 hours — inside the default 24h reminder window.
        Long movieId = movieRepository.findAll().get(0).getId();
        Long screenId = screenRepository.findAll().get(0).getId();
        Long tierId = pricingTierRepository.findByName("REGULAR").orElseThrow().getId();
        Instant start = Instant.now().plus(2, ChronoUnit.HOURS);

        ShowSummaryResponse show = adminService.createShow(new CreateShowRequest(
                movieId, screenId, tierId, null, start, start.plus(2, ChronoUnit.HOURS), new BigDecimal("200")));

        ShowSeat seat = showSeatRepository.findByShowWithSeat(show.showId()).get(0);
        HoldResponse hold = holdService.hold(show.showId(), List.of(seat.getSeat().getId()), null);
        BookingResponse booking = bookingService.confirm(
                new CreateBookingRequest(show.showId(), hold.holdToken(), null), "alice", null);

        reminderScheduler.enqueueReminders();
        assertThat(outboxRepository.countByBooking_IdAndType(booking.bookingId(), NotificationType.REMINDER))
                .as("one reminder after the first sweep").isEqualTo(1);
        assertThat(bookingRepository.findById(booking.bookingId()).orElseThrow().isReminderEnqueued()).isTrue();

        // A second sweep must not enqueue a duplicate.
        reminderScheduler.enqueueReminders();
        assertThat(outboxRepository.countByBooking_IdAndType(booking.bookingId(), NotificationType.REMINDER))
                .as("still one reminder after a second sweep").isEqualTo(1);
    }
}
