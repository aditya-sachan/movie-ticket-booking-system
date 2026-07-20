package com.aditya.movieticketing.scheduler;

import com.aditya.movieticketing.domain.Booking;
import com.aditya.movieticketing.domain.BookingStatus;
import com.aditya.movieticketing.domain.NotificationOutbox;
import com.aditya.movieticketing.domain.NotificationType;
import com.aditya.movieticketing.repository.BookingRepository;
import com.aditya.movieticketing.repository.NotificationOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Enqueues a one-time showtime reminder for each confirmed booking whose show starts within the
 * reminder lead window. It only writes to the transactional outbox (the existing poller delivers
 * it), so reminders never block anything, and a per-booking flag makes each booking remind exactly
 * once across sweeps.
 */
@Component
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final BookingRepository bookingRepository;
    private final NotificationOutboxRepository outboxRepository;
    private final long leadHours;

    public ReminderScheduler(BookingRepository bookingRepository,
                             NotificationOutboxRepository outboxRepository,
                             @Value("${booking.reminder.lead-hours:24}") long leadHours) {
        this.bookingRepository = bookingRepository;
        this.outboxRepository = outboxRepository;
        this.leadHours = leadHours;
    }

    @Scheduled(fixedDelayString = "${booking.reminder.sweep-interval-ms:300000}")
    @Transactional
    public void enqueueReminders() {
        Instant now = Instant.now();
        Instant until = now.plus(Duration.ofHours(leadHours));

        List<Booking> due = bookingRepository.findBookingsNeedingReminder(BookingStatus.CONFIRMED, now, until);
        for (Booking booking : due) {
            String payload = "Reminder: your booking " + booking.getId() + " for \""
                    + booking.getShow().getMovie().getTitle() + "\" starts at " + booking.getShow().getStartsAt();
            outboxRepository.save(new NotificationOutbox(
                    booking, NotificationType.REMINDER, booking.getUser().getUsername(), payload));
            booking.setReminderEnqueued(true);
        }
        if (!due.isEmpty()) {
            log.info("Enqueued {} showtime reminder(s)", due.size());
        }
    }
}
