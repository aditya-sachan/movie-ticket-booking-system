package com.aditya.movieticketing.repository;

import com.aditya.movieticketing.domain.Theater;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TheaterRepository extends JpaRepository<Theater, Long> {
    List<Theater> findByCity_IdOrderByName(Long cityId);
}
