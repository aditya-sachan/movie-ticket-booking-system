package com.aditya.movieticketing.scheduler;

import com.aditya.movieticketing.domain.NotificationOutbox;
import com.aditya.movieticketing.domain.NotificationStatus;
import com.aditya.movieticketing.repository.NotificationOutboxRepository;
import com.aditya.movieticketing.service.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Drains the transactional outbox: picks up PENDING notification rows and delivers them via the
 * {@link NotificationSender} stub. Runs out of band so notification delivery never blocks booking.
 */
@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationSender notificationSender;

    public OutboxPoller(NotificationOutboxRepository outboxRepository,
                        NotificationSender notificationSender) {
        this.outboxRepository = outboxRepository;
        this.notificationSender = notificationSender;
    }

    @Scheduled(fixedDelayString = "${booking.outbox.poll-interval-ms:5000}")
    @Transactional
    public void poll() {
        List<NotificationOutbox> pending =
                outboxRepository.findTop100ByStatusOrderByCreatedAtAsc(NotificationStatus.PENDING);
        for (NotificationOutbox notification : pending) {
            notification.setAttempts(notification.getAttempts() + 1);
            try {
                notificationSender.send(notification);
                notification.setStatus(NotificationStatus.SENT);
                notification.setSentAt(Instant.now());
            } catch (RuntimeException ex) {
                notification.setStatus(NotificationStatus.FAILED);
                log.warn("Failed to deliver notification {}", notification.getId(), ex);
            }
        }
    }
}
