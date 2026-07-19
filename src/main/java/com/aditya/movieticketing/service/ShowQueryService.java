package com.aditya.movieticketing.service;

import com.aditya.movieticketing.domain.ShowSeat;
import com.aditya.movieticketing.exception.ShowNotFoundException;
import com.aditya.movieticketing.repository.ShowRepository;
import com.aditya.movieticketing.repository.ShowSeatRepository;
import com.aditya.movieticketing.web.dto.ShowSeatResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read-side queries for shows. For slice 2 this exposes the seat map of a show so a client can
 * see which seats to hold. Full browse/search lands in slice 4.
 */
@Service
public class ShowQueryService {

    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;

    public ShowQueryService(ShowRepository showRepository, ShowSeatRepository showSeatRepository) {
        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
    }

    @Transactional(readOnly = true)
    public List<ShowSeatResponse> seatMap(Long showId) {
        if (!showRepository.existsById(showId)) {
            throw new ShowNotFoundException(showId);
        }
        List<ShowSeat> showSeats = showSeatRepository.findByShowWithSeat(showId);
        return showSeats.stream()
                .map(this::toResponse)
                .toList();
    }

    private ShowSeatResponse toResponse(ShowSeat showSeat) {
        return new ShowSeatResponse(
                showSeat.getId(),
                showSeat.getSeat().getId(),
                showSeat.getSeat().getRowLabel(),
                showSeat.getSeat().getSeatNumber(),
                showSeat.getSeat().getSeatClass().getName(),
                showSeat.getStatus().name()
        );
    }
}
