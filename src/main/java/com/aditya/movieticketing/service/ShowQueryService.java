package com.aditya.movieticketing.service;

import com.aditya.movieticketing.domain.Show;
import com.aditya.movieticketing.domain.ShowSeat;
import com.aditya.movieticketing.exception.ShowNotFoundException;
import com.aditya.movieticketing.repository.ShowRepository;
import com.aditya.movieticketing.repository.ShowSeatRepository;
import com.aditya.movieticketing.web.dto.ShowSeatResponse;
import com.aditya.movieticketing.web.dto.ShowSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Read-side queries for shows: browse/search with filters, and the per-show seat map.
 */
@Service
public class ShowQueryService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;

    public ShowQueryService(ShowRepository showRepository, ShowSeatRepository showSeatRepository) {
        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
    }

    @Transactional(readOnly = true)
    public List<ShowSummaryResponse> search(Long cityId, Long movieId, LocalDate date) {
        Instant from = date == null ? null : date.atStartOfDay(ZONE).toInstant();
        Instant to = date == null ? null : date.plusDays(1).atStartOfDay(ZONE).toInstant();
        return showRepository.search(cityId, movieId, from, to).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ShowSeatResponse> seatMap(Long showId) {
        if (!showRepository.existsById(showId)) {
            throw new ShowNotFoundException(showId);
        }
        return showSeatRepository.findByShowWithSeat(showId).stream()
                .map(this::toSeatResponse)
                .toList();
    }

    private ShowSummaryResponse toSummary(Show show) {
        return new ShowSummaryResponse(
                show.getId(),
                show.getMovie().getId(),
                show.getMovie().getTitle(),
                show.getMovie().getLanguage(),
                show.getScreen().getTheater().getId(),
                show.getScreen().getTheater().getName(),
                show.getScreen().getTheater().getCity().getName(),
                show.getScreen().getName(),
                show.getPricingTier().getName(),
                show.getStartsAt(),
                show.getEndsAt(),
                show.getBasePrice());
    }

    private ShowSeatResponse toSeatResponse(ShowSeat showSeat) {
        return new ShowSeatResponse(
                showSeat.getId(),
                showSeat.getSeat().getId(),
                showSeat.getSeat().getRowLabel(),
                showSeat.getSeat().getSeatNumber(),
                showSeat.getSeat().getSeatClass().getName(),
                showSeat.getStatus().name());
    }
}
