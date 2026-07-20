package com.aditya.movieticketing.booking;

import com.aditya.movieticketing.AbstractIntegrationTest;
import com.aditya.movieticketing.domain.ShowSeat;
import com.aditya.movieticketing.exception.HoldExpiredException;
import com.aditya.movieticketing.repository.BookingSeatRepository;
import com.aditya.movieticketing.repository.ShowRepository;
import com.aditya.movieticketing.repository.ShowSeatRepository;
import com.aditya.movieticketing.service.BookingService;
import com.aditya.movieticketing.service.HoldService;
import com.aditya.movieticketing.web.dto.BookingResponse;
import com.aditya.movieticketing.web.dto.CreateBookingRequest;
import com.aditya.movieticketing.web.dto.HoldResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Idempotent booking confirmation: retrying a confirm with the same Idempotency-Key returns the
 * original booking rather than creating a duplicate — or failing with HoldExpired because the hold
 * was consumed on the first attempt.
 */
class IdempotentBookingTest extends AbstractIntegrationTest {

    @Autowired
    private HoldService holdService;
    @Autowired
    private BookingService bookingService;
    @Autowired
    private ShowRepository showRepository;
    @Autowired
    private ShowSeatRepository showSeatRepository;
    @Autowired
    private BookingSeatRepository bookingSeatRepository;

    private Long anyShowId() {
        return showRepository.findAll().stream().findFirst().orElseThrow().getId();
    }

    private ShowSeat seatAt(Long showId, int index) {
        return showSeatRepository.findByShowWithSeat(showId).get(index);
    }

    @Test
    @DisplayName("a retried confirm with the same Idempotency-Key returns the original booking, not a duplicate")
    void idempotentRetryReturnsSameBooking() {
        Long showId = anyShowId();
        ShowSeat target = seatAt(showId, 20);
        Long showSeatId = target.getId();

        HoldResponse hold = holdService.hold(showId, List.of(target.getSeat().getId()), null);
        String key = "idem-" + UUID.randomUUID();
        var request = new CreateBookingRequest(showId, hold.holdToken(), null);

        BookingResponse first = bookingService.confirm(request, "alice", key);
        // The hold is now consumed; without idempotency this retry would fail with HoldExpired.
        BookingResponse replay = bookingService.confirm(request, "alice", key);

        assertThat(replay.bookingId()).isEqualTo(first.bookingId());
        assertThat(replay.total()).isEqualByComparingTo(first.total());
        assertThat(replay.seatIds()).isEqualTo(first.seatIds());
        assertThat(bookingSeatRepository.countByShowSeat_IdAndActiveTrue(showSeatId))
                .as("still exactly one active booking for the seat").isEqualTo(1);
    }

    @Test
    @DisplayName("without the matching key, retrying a consumed hold fails (replay depends on the key)")
    void differentKeyOnConsumedHoldFails() {
        Long showId = anyShowId();
        ShowSeat target = seatAt(showId, 21);
        HoldResponse hold = holdService.hold(showId, List.of(target.getSeat().getId()), null);
        var request = new CreateBookingRequest(showId, hold.holdToken(), null);

        bookingService.confirm(request, "alice", "keyA-" + UUID.randomUUID());

        assertThatThrownBy(() -> bookingService.confirm(request, "alice", "keyB-" + UUID.randomUUID()))
                .isInstanceOf(HoldExpiredException.class);
    }
}
