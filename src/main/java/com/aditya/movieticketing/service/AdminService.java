package com.aditya.movieticketing.service;

import com.aditya.movieticketing.domain.City;
import com.aditya.movieticketing.domain.Movie;
import com.aditya.movieticketing.domain.PricingTier;
import com.aditya.movieticketing.domain.RefundPolicy;
import com.aditya.movieticketing.domain.Screen;
import com.aditya.movieticketing.domain.Seat;
import com.aditya.movieticketing.domain.Show;
import com.aditya.movieticketing.domain.ShowSeat;
import com.aditya.movieticketing.domain.Theater;
import com.aditya.movieticketing.exception.InvalidBookingStateException;
import com.aditya.movieticketing.exception.ResourceNotFoundException;
import com.aditya.movieticketing.repository.CityRepository;
import com.aditya.movieticketing.repository.MovieRepository;
import com.aditya.movieticketing.repository.PricingTierRepository;
import com.aditya.movieticketing.repository.RefundPolicyRepository;
import com.aditya.movieticketing.repository.ScreenRepository;
import com.aditya.movieticketing.repository.SeatRepository;
import com.aditya.movieticketing.repository.ShowRepository;
import com.aditya.movieticketing.repository.ShowSeatRepository;
import com.aditya.movieticketing.repository.TheaterRepository;
import com.aditya.movieticketing.web.dto.CityResponse;
import com.aditya.movieticketing.web.dto.CreateCityRequest;
import com.aditya.movieticketing.web.dto.CreateShowRequest;
import com.aditya.movieticketing.web.dto.CreateTheaterRequest;
import com.aditya.movieticketing.web.dto.ShowSummaryResponse;
import com.aditya.movieticketing.web.dto.TheaterResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Admin management of cities, theaters, and shows. Creating a show also materializes one
 * {@code show_seat} row (AVAILABLE) per physical seat on the chosen screen — the point at which
 * a show becomes bookable.
 */
@Service
public class AdminService {

    private final CityRepository cityRepository;
    private final TheaterRepository theaterRepository;
    private final ScreenRepository screenRepository;
    private final SeatRepository seatRepository;
    private final MovieRepository movieRepository;
    private final PricingTierRepository pricingTierRepository;
    private final RefundPolicyRepository refundPolicyRepository;
    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;

    public AdminService(CityRepository cityRepository,
                        TheaterRepository theaterRepository,
                        ScreenRepository screenRepository,
                        SeatRepository seatRepository,
                        MovieRepository movieRepository,
                        PricingTierRepository pricingTierRepository,
                        RefundPolicyRepository refundPolicyRepository,
                        ShowRepository showRepository,
                        ShowSeatRepository showSeatRepository) {
        this.cityRepository = cityRepository;
        this.theaterRepository = theaterRepository;
        this.screenRepository = screenRepository;
        this.seatRepository = seatRepository;
        this.movieRepository = movieRepository;
        this.pricingTierRepository = pricingTierRepository;
        this.refundPolicyRepository = refundPolicyRepository;
        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
    }

    @Transactional
    public CityResponse createCity(CreateCityRequest request) {
        City city = cityRepository.save(new City(request.name(), request.state()));
        return toCity(city);
    }

    @Transactional(readOnly = true)
    public List<CityResponse> listCities() {
        return cityRepository.findAll().stream().map(this::toCity).toList();
    }

    @Transactional
    public TheaterResponse createTheater(CreateTheaterRequest request) {
        City city = cityRepository.findById(request.cityId())
                .orElseThrow(() -> new ResourceNotFoundException("No city with id " + request.cityId()));
        Theater theater = theaterRepository.save(new Theater(city, request.name(), request.address()));
        return toTheater(theater);
    }

    @Transactional(readOnly = true)
    public List<TheaterResponse> listTheaters(Long cityId) {
        List<Theater> theaters = cityId == null
                ? theaterRepository.findAll()
                : theaterRepository.findByCity_IdOrderByName(cityId);
        return theaters.stream().map(this::toTheater).toList();
    }

    @Transactional
    public ShowSummaryResponse createShow(CreateShowRequest request) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new InvalidBookingStateException("endsAt must be after startsAt");
        }
        Movie movie = movieRepository.findById(request.movieId())
                .orElseThrow(() -> new ResourceNotFoundException("No movie with id " + request.movieId()));
        Screen screen = screenRepository.findById(request.screenId())
                .orElseThrow(() -> new ResourceNotFoundException("No screen with id " + request.screenId()));
        PricingTier tier = pricingTierRepository.findById(request.pricingTierId())
                .orElseThrow(() -> new ResourceNotFoundException("No pricing tier with id " + request.pricingTierId()));
        RefundPolicy refundPolicy = request.refundPolicyId() == null ? null
                : refundPolicyRepository.findById(request.refundPolicyId())
                .orElseThrow(() -> new ResourceNotFoundException("No refund policy with id " + request.refundPolicyId()));

        Show show = showRepository.save(new Show(movie, screen, tier, refundPolicy,
                request.startsAt(), request.endsAt(), request.basePrice()));

        // Materialize one AVAILABLE show_seat per physical seat on the screen.
        List<Seat> seats = seatRepository.findByScreen_IdOrderById(screen.getId());
        for (Seat seat : seats) {
            showSeatRepository.save(new ShowSeat(show, seat));
        }

        return new ShowSummaryResponse(show.getId(), movie.getId(), movie.getTitle(), movie.getLanguage(),
                screen.getTheater().getId(), screen.getTheater().getName(),
                screen.getTheater().getCity().getName(), screen.getName(), tier.getName(),
                show.getStartsAt(), show.getEndsAt(), show.getBasePrice());
    }

    private CityResponse toCity(City city) {
        return new CityResponse(city.getId(), city.getName(), city.getState());
    }

    private TheaterResponse toTheater(Theater theater) {
        return new TheaterResponse(theater.getId(), theater.getCity().getId(),
                theater.getName(), theater.getAddress());
    }
}
