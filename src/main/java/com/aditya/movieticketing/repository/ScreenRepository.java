package com.aditya.movieticketing.repository;

import com.aditya.movieticketing.domain.Screen;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScreenRepository extends JpaRepository<Screen, Long> {
}
