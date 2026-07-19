package com.aditya.movieticketing.service;

import com.aditya.movieticketing.domain.NotificationOutbox;

/**
 * Delivery seam for notifications. Slice keeps a logging stub; real email/SMS providers are out of
 * scope and would plug in here without touching the booking path or the outbox poller.
 */
public interface NotificationSender {

    void send(NotificationOutbox notification);
}
