package com.aditya.movieticketing.service;

import com.aditya.movieticketing.domain.NotificationOutbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Logging stub delivery. Stands in for a real email/SMS provider (out of scope).
 */
@Component
public class LoggingNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    @Override
    public void send(NotificationOutbox notification) {
        log.info("[NOTIFY {}] to={} :: {}",
                notification.getType(), notification.getRecipient(), notification.getPayload());
    }
}
