package com.aditya.movieticketing.repository;

import com.aditya.movieticketing.domain.SeatClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SeatClassRepository extends JpaRepository<SeatClass, Long> {
    Optional<SeatClass> findByName(String name);
}
