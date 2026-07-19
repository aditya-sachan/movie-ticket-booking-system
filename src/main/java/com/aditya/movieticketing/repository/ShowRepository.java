package com.aditya.movieticketing.repository;

import com.aditya.movieticketing.domain.Show;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShowRepository extends JpaRepository<Show, Long> {
}
