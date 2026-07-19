package com.aditya.movieticketing.repository;

import com.aditya.movieticketing.domain.Show;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ShowRepository extends JpaRepository<Show, Long> {

    /**
     * Browse/search shows with optional city, movie, and time-window filters (each null = ignore).
     */
    @Query("""
            select s from Show s
            join s.screen sc
            join sc.theater t
            join t.city c
            join s.movie m
            where (:cityId is null or c.id = :cityId)
              and (:movieId is null or m.id = :movieId)
              and (:from is null or s.startsAt >= :from)
              and (:to is null or s.startsAt < :to)
            order by s.startsAt
            """)
    List<Show> search(@Param("cityId") Long cityId,
                      @Param("movieId") Long movieId,
                      @Param("from") Instant from,
                      @Param("to") Instant to);
}
