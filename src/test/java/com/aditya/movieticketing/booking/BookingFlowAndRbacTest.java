package com.aditya.movieticketing.booking;

import com.aditya.movieticketing.AbstractIntegrationTest;
import com.aditya.movieticketing.domain.ShowSeat;
import com.aditya.movieticketing.repository.MovieRepository;
import com.aditya.movieticketing.repository.PricingTierRepository;
import com.aditya.movieticketing.repository.ShowRepository;
import com.aditya.movieticketing.repository.ShowSeatRepository;
import com.aditya.movieticketing.repository.TheaterRepository;
import com.aditya.movieticketing.service.AdminService;
import com.aditya.movieticketing.web.dto.CreateScreenRequest;
import com.aditya.movieticketing.web.dto.CreateSeatsRequest;
import com.aditya.movieticketing.web.dto.CreateShowRequest;
import com.aditya.movieticketing.web.dto.SeatResponse;
import com.aditya.movieticketing.web.dto.SeatRowSpec;
import com.aditya.movieticketing.web.dto.ShowSummaryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end happy path (browse → hold → confirm → cancel) plus role-based access control:
 * unauthenticated is rejected, a customer cannot cancel another customer's booking, and an admin
 * cannot use a customer-only endpoint.
 */
class BookingFlowAndRbacTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ShowRepository showRepository;
    @Autowired
    private ShowSeatRepository showSeatRepository;
    @Autowired
    private AdminService adminService;
    @Autowired
    private TheaterRepository theaterRepository;
    @Autowired
    private MovieRepository movieRepository;
    @Autowired
    private PricingTierRepository pricingTierRepository;

    private Long anyShowId() {
        return showRepository.findAll().stream().findFirst().orElseThrow().getId();
    }

    private Long seatIdAt(Long showId, int index) {
        List<ShowSeat> seats = showSeatRepository.findByShowWithSeat(showId);
        return seats.get(index).getSeat().getId();
    }

    /**
     * A fresh show with its own clean 10-seat row, isolated from other tests' seat state so the
     * seat-selection (no-orphan) rule is deterministic. Returns the show id and its seat ids in
     * seat-number order.
     */
    private ShowSeats freshShow() {
        Long theaterId = theaterRepository.findAll().get(0).getId();
        Long movieId = movieRepository.findAll().get(0).getId();
        Long tierId = pricingTierRepository.findByName("REGULAR").orElseThrow().getId();
        var screen = adminService.createScreen(new CreateScreenRequest(theaterId, "flow-" + UUID.randomUUID()));
        List<SeatResponse> seats = adminService.addSeats(screen.id(),
                new CreateSeatsRequest(List.of(new SeatRowSpec("R", 10, "REGULAR"))));
        Instant start = Instant.now().plus(6, ChronoUnit.DAYS);
        ShowSummaryResponse show = adminService.createShow(new CreateShowRequest(
                movieId, screen.id(), tierId, null, start, start.plus(2, ChronoUnit.HOURS), new BigDecimal("200")));
        return new ShowSeats(show.showId(), seats.stream().map(SeatResponse::id).toList());
    }

    private record ShowSeats(long showId, List<Long> seatIds) {
    }

    @Test
    @DisplayName("happy path: browse → hold → confirm → cancel")
    void happyPath() throws Exception {
        ShowSeats fixture = freshShow();
        long showId = fixture.showId();
        long seatA = fixture.seatIds().get(0); // seat R1
        long seatB = fixture.seatIds().get(1); // seat R2 — contiguous, leaves no orphan

        // browse
        mockMvc.perform(get("/shows/{id}/seats", showId).with(httpBasic("alice", "alice123")))
                .andExpect(status().isOk());

        // hold
        String holdBody = """
                {"seatIds":[%d,%d]}""".formatted(seatA, seatB);
        String holdJson = mockMvc.perform(post("/shows/{id}/holds", showId)
                        .with(httpBasic("alice", "alice123"))
                        .contentType(MediaType.APPLICATION_JSON).content(holdBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String holdToken = objectMapper.readTree(holdJson).get("holdToken").asText();

        // confirm
        String bookBody = """
                {"showId":%d,"holdToken":"%s"}""".formatted(showId, holdToken);
        String bookJson = mockMvc.perform(post("/bookings")
                        .with(httpBasic("alice", "alice123"))
                        .contentType(MediaType.APPLICATION_JSON).content(bookBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andReturn().getResponse().getContentAsString();
        long bookingId = objectMapper.readTree(bookJson).get("bookingId").asLong();

        // cancel
        mockMvc.perform(post("/bookings/{id}/cancel", bookingId).with(httpBasic("alice", "alice123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("unauthenticated request is rejected with 401")
    void unauthenticated() throws Exception {
        mockMvc.perform(get("/shows/{id}/seats", anyShowId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a customer cannot cancel another customer's booking (403)")
    void customerCannotCancelOthersBooking() throws Exception {
        ShowSeats fixture = freshShow();
        long showId = fixture.showId();
        long seat = fixture.seatIds().get(0); // seat R1 at the row boundary — no orphan

        String holdJson = mockMvc.perform(post("/shows/{id}/holds", showId)
                        .with(httpBasic("alice", "alice123"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"seatIds\":[" + seat + "]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String holdToken = objectMapper.readTree(holdJson).get("holdToken").asText();

        String bookJson = mockMvc.perform(post("/bookings")
                        .with(httpBasic("alice", "alice123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showId\":" + showId + ",\"holdToken\":\"" + holdToken + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long bookingId = objectMapper.readTree(bookJson).get("bookingId").asLong();

        // bob (another customer) may not cancel alice's booking
        mockMvc.perform(post("/bookings/{id}/cancel", bookingId).with(httpBasic("bob", "bob123")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an admin cannot use a customer-only endpoint (403)")
    void adminCannotHold() throws Exception {
        Long showId = anyShowId();
        long seat = seatIdAt(showId, 13);
        mockMvc.perform(post("/shows/{id}/holds", showId)
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"seatIds\":[" + seat + "]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a customer cannot hit an admin endpoint (403)")
    void customerCannotHitAdminEndpoint() throws Exception {
        mockMvc.perform(post("/admin/cities")
                        .with(httpBasic("alice", "alice123"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Pune\",\"state\":\"MH\"}"))
                .andExpect(status().isForbidden());

        // and an admin CAN
        mockMvc.perform(post("/admin/cities")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Pune\",\"state\":\"MH\"}"))
                .andExpect(status().isCreated());
    }
}
