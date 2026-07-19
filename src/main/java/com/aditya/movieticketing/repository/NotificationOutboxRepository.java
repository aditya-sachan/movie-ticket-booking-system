package com.aditya.movieticketing.repository;

import com.aditya.movieticketing.domain.NotificationOutbox;
import com.aditya.movieticketing.domain.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    List<NotificationOutbox> findTop100ByStatusOrderByCreatedAtAsc(NotificationStatus status);
}
