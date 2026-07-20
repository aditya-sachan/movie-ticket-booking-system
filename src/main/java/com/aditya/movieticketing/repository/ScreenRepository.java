package com.aditya.movieticketing.repository;

import com.aditya.movieticketing.domain.Screen;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScreenRepository extends JpaRepository<Screen, Long> {
    List<Screen> findByTheater_IdOrderByName(Long theaterId);
}
