package com.aditya.movieticketing.repository;

import com.aditya.movieticketing.domain.Movie;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieRepository extends JpaRepository<Movie, Long> {
}
