package com.aditya.movieticketing.booking;

import com.aditya.movieticketing.AbstractIntegrationTest;
import com.aditya.movieticketing.exception.InvalidSeatSelectionException;
import com.aditya.movieticketing.repository.MovieRepository;
import com.aditya.movieticketing.repository.PricingTierRepository;
import com.aditya.movieticketing.repository.TheaterRepository;
import com.aditya.movieticketing.service.AdminService;
import com.aditya.movieticketing.service.BookingService;
import com.aditya.movieticketing.service.HoldService;
import com.aditya.movieticketing.web.dto.CreateBookingRequest;
import com.aditya.movieticketing.web.dto.CreateScreenRequest;
import com.aditya.movieticketing.web.dto.CreateSeatsRequest;
import com.aditya.movieticketing.web.dto.CreateShowRequest;
import com.aditya.movieticketing.web.dto.HoldResponse;
import com.aditya.movieticketing.web.dto.SeatResponse;
import com.aditya.movieticketing.web.dto.SeatRowSpec;
import com.aditya.movieticketing.web.dto.ShowSummaryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Seat-selection rules end-to-end through HoldService: the max-seats cap and the no-orphan-seat
 * rule, exercised on freshly admin-created single-row screens (isolated from other tests' state).
 */
class SeatSelectionIntegrationTest extends AbstractIntegrationTest {

    @Autowired private AdminService adminService;
    @Autowired private HoldService holdService;
    @Autowired private BookingService bookingService;
    @Autowired private TheaterRepository theaterRepository;
    @Autowired private MovieRepository movieRepository;
    @Autowired private PricingTierRepository pricingTierRepository;

    private record RowShow(Long showId, List<SeatResponse> seats) {
        Long seatNumber(int n) {
            return seats.stream().filter(s -> s.seatNumber() == n).findFirst().orElseThrow().id();
        }
    }

    private RowShow newSingleRowShow(String rowLabel, int count) {
        Long theaterId = theaterRepository.findAll().get(0).getId();
        Long movieId = movieRepository.findAll().get(0).getId();
        Long tierId = pricingTierRepository.findByName("REGULAR").orElseThrow().getId();

        var screen = adminService.createScreen(new CreateScreenRequest(theaterId, "SS-" + UUID.randomUUID()));
        List<SeatResponse> seats = adminService.addSeats(screen.id(),
                new CreateSeatsRequest(List.of(new SeatRowSpec(rowLabel, count, "REGULAR"))));
        Instant start = Instant.now().plus(5, ChronoUnit.DAYS);
        ShowSummaryResponse show = adminService.createShow(new CreateShowRequest(
                movieId, screen.id(), tierId, null, start, start.plus(2, ChronoUnit.HOURS), new BigDecimal("200")));
        return new RowShow(show.showId(), seats);
    }

    @Test
    @DisplayName("holding more than the max seats per booking is rejected")
    void maxSeatsRejected() {
        RowShow ctx = newSingleRowShow("M", 12);
        List<Long> eleven = ctx.seats().stream().limit(11).map(SeatResponse::id).toList();
        assertThatThrownBy(() -> holdService.hold(ctx.showId(), eleven, null))
                .isInstanceOf(InvalidSeatSelectionException.class);
    }

    @Test
    @DisplayName("a hold that would strand a lone seat between occupied seats is rejected")
    void orphanSeatRejected() {
        RowShow ctx = newSingleRowShow("Z", 3);
        // Book Z1.
        HoldResponse hold = holdService.hold(ctx.showId(), List.of(ctx.seatNumber(1)), null);
        bookingService.confirm(new CreateBookingRequest(ctx.showId(), hold.holdToken(), null), "alice", null);
        // Holding Z3 now leaves Z2 isolated between Z1 (booked) and Z3 (held) -> rejected.
        assertThatThrownBy(() -> holdService.hold(ctx.showId(), List.of(ctx.seatNumber(3)), null))
                .isInstanceOf(InvalidSeatSelectionException.class);
    }

    @Test
    @DisplayName("holding the contiguous block leaves no orphan and is allowed")
    void contiguousBlockAllowed() {
        RowShow ctx = newSingleRowShow("C", 3);
        assertThatCode(() -> holdService.hold(ctx.showId(),
                List.of(ctx.seatNumber(1), ctx.seatNumber(2), ctx.seatNumber(3)), null))
                .doesNotThrowAnyException();
    }
}
