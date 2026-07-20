package com.aditya.movieticketing.service;

import com.aditya.movieticketing.domain.City;
import com.aditya.movieticketing.domain.Movie;
import com.aditya.movieticketing.domain.PricingTier;
import com.aditya.movieticketing.domain.RefundPolicy;
import com.aditya.movieticketing.domain.RefundRule;
import com.aditya.movieticketing.domain.Screen;
import com.aditya.movieticketing.domain.Seat;
import com.aditya.movieticketing.domain.SeatClass;
import com.aditya.movieticketing.domain.Show;
import com.aditya.movieticketing.domain.ShowSeat;
import com.aditya.movieticketing.domain.Theater;
import com.aditya.movieticketing.exception.InvalidBookingStateException;
import com.aditya.movieticketing.exception.ResourceNotFoundException;
import com.aditya.movieticketing.repository.CityRepository;
import com.aditya.movieticketing.repository.MovieRepository;
import com.aditya.movieticketing.repository.PricingTierRepository;
import com.aditya.movieticketing.repository.RefundPolicyRepository;
import com.aditya.movieticketing.repository.RefundRuleRepository;
import com.aditya.movieticketing.repository.ScreenRepository;
import com.aditya.movieticketing.repository.SeatClassRepository;
import com.aditya.movieticketing.repository.SeatRepository;
import com.aditya.movieticketing.repository.ShowRepository;
import com.aditya.movieticketing.repository.ShowSeatRepository;
import com.aditya.movieticketing.repository.TheaterRepository;
import com.aditya.movieticketing.web.dto.CityResponse;
import com.aditya.movieticketing.web.dto.CreateCityRequest;
import com.aditya.movieticketing.web.dto.CreatePricingTierRequest;
import com.aditya.movieticketing.web.dto.CreateRefundPolicyRequest;
import com.aditya.movieticketing.web.dto.CreateScreenRequest;
import com.aditya.movieticketing.web.dto.CreateSeatsRequest;
import com.aditya.movieticketing.web.dto.CreateShowRequest;
import com.aditya.movieticketing.web.dto.CreateTheaterRequest;
import com.aditya.movieticketing.web.dto.PricingTierResponse;
import com.aditya.movieticketing.web.dto.RefundPolicyResponse;
import com.aditya.movieticketing.web.dto.RefundRuleResponse;
import com.aditya.movieticketing.web.dto.RefundRuleSpec;
import com.aditya.movieticketing.web.dto.ScreenResponse;
import com.aditya.movieticketing.web.dto.SeatResponse;
import com.aditya.movieticketing.web.dto.SeatRowSpec;
import com.aditya.movieticketing.web.dto.ShowSummaryResponse;
import com.aditya.movieticketing.web.dto.TheaterResponse;
import com.aditya.movieticketing.web.dto.UpdatePricingTierRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin management for everything the role owns: cities, theaters, seat layouts (screens + physical
 * seats), shows, pricing tiers, and refund policies. Creating a show materializes one AVAILABLE
 * {@code show_seat} per physical seat on the chosen screen.
 */
@Service
public class AdminService {

    private final CityRepository cityRepository;
    private final TheaterRepository theaterRepository;
    private final ScreenRepository screenRepository;
    private final SeatRepository seatRepository;
    private final SeatClassRepository seatClassRepository;
    private final MovieRepository movieRepository;
    private final PricingTierRepository pricingTierRepository;
    private final RefundPolicyRepository refundPolicyRepository;
    private final RefundRuleRepository refundRuleRepository;
    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;

    public AdminService(CityRepository cityRepository,
                        TheaterRepository theaterRepository,
                        ScreenRepository screenRepository,
                        SeatRepository seatRepository,
                        SeatClassRepository seatClassRepository,
                        MovieRepository movieRepository,
                        PricingTierRepository pricingTierRepository,
                        RefundPolicyRepository refundPolicyRepository,
                        RefundRuleRepository refundRuleRepository,
                        ShowRepository showRepository,
                        ShowSeatRepository showSeatRepository) {
        this.cityRepository = cityRepository;
        this.theaterRepository = theaterRepository;
        this.screenRepository = screenRepository;
        this.seatRepository = seatRepository;
        this.seatClassRepository = seatClassRepository;
        this.movieRepository = movieRepository;
        this.pricingTierRepository = pricingTierRepository;
        this.refundPolicyRepository = refundPolicyRepository;
        this.refundRuleRepository = refundRuleRepository;
        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
    }

    // --- Cities -------------------------------------------------------------

    @Transactional
    public CityResponse createCity(CreateCityRequest request) {
        return toCity(cityRepository.save(new City(request.name(), request.state())));
    }

    @Transactional(readOnly = true)
    public List<CityResponse> listCities() {
        return cityRepository.findAll().stream().map(this::toCity).toList();
    }

    // --- Theaters -----------------------------------------------------------

    @Transactional
    public TheaterResponse createTheater(CreateTheaterRequest request) {
        City city = cityRepository.findById(request.cityId())
                .orElseThrow(() -> new ResourceNotFoundException("No city with id " + request.cityId()));
        return toTheater(theaterRepository.save(new Theater(city, request.name(), request.address())));
    }

    @Transactional(readOnly = true)
    public List<TheaterResponse> listTheaters(Long cityId) {
        List<Theater> theaters = cityId == null
                ? theaterRepository.findAll()
                : theaterRepository.findByCity_IdOrderByName(cityId);
        return theaters.stream().map(this::toTheater).toList();
    }

    // --- Seat layouts: screens + physical seats -----------------------------

    @Transactional
    public ScreenResponse createScreen(CreateScreenRequest request) {
        Theater theater = theaterRepository.findById(request.theaterId())
                .orElseThrow(() -> new ResourceNotFoundException("No theater with id " + request.theaterId()));
        return toScreen(screenRepository.save(new Screen(theater, request.name())));
    }

    @Transactional(readOnly = true)
    public List<ScreenResponse> listScreens(Long theaterId) {
        List<Screen> screens = theaterId == null
                ? screenRepository.findAll()
                : screenRepository.findByTheater_IdOrderByName(theaterId);
        return screens.stream().map(this::toScreen).toList();
    }

    @Transactional
    public List<SeatResponse> addSeats(Long screenId, CreateSeatsRequest request) {
        Screen screen = screenRepository.findById(screenId)
                .orElseThrow(() -> new ResourceNotFoundException("No screen with id " + screenId));
        List<Seat> created = new ArrayList<>();
        for (SeatRowSpec row : request.rows()) {
            SeatClass seatClass = seatClassRepository.findByName(row.seatClassName())
                    .orElseThrow(() -> new ResourceNotFoundException("No seat class named " + row.seatClassName()));
            for (int number = 1; number <= row.count(); number++) {
                created.add(seatRepository.save(new Seat(screen, seatClass, row.rowLabel(), number)));
            }
        }
        return created.stream().map(this::toSeat).toList();
    }

    @Transactional(readOnly = true)
    public List<SeatResponse> listSeats(Long screenId) {
        if (!screenRepository.existsById(screenId)) {
            throw new ResourceNotFoundException("No screen with id " + screenId);
        }
        return seatRepository.findByScreen_IdOrderById(screenId).stream().map(this::toSeat).toList();
    }

    // --- Shows --------------------------------------------------------------

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

        for (Seat seat : seatRepository.findByScreen_IdOrderById(screen.getId())) {
            showSeatRepository.save(new ShowSeat(show, seat));
        }

        return new ShowSummaryResponse(show.getId(), movie.getId(), movie.getTitle(), movie.getLanguage(),
                screen.getTheater().getId(), screen.getTheater().getName(),
                screen.getTheater().getCity().getName(), screen.getName(), tier.getName(),
                show.getStartsAt(), show.getEndsAt(), show.getBasePrice());
    }

    // --- Pricing tiers ------------------------------------------------------

    @Transactional
    public PricingTierResponse createPricingTier(CreatePricingTierRequest request) {
        return toTier(pricingTierRepository.save(new PricingTier(request.name(), request.multiplier())));
    }

    @Transactional(readOnly = true)
    public List<PricingTierResponse> listPricingTiers() {
        return pricingTierRepository.findAll().stream().map(this::toTier).toList();
    }

    @Transactional
    public PricingTierResponse updatePricingTier(Long id, UpdatePricingTierRequest request) {
        PricingTier tier = pricingTierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No pricing tier with id " + id));
        tier.setMultiplier(request.multiplier());
        return toTier(tier);
    }

    // --- Refund policies ----------------------------------------------------

    @Transactional
    public RefundPolicyResponse createRefundPolicy(CreateRefundPolicyRequest request) {
        RefundPolicy policy = refundPolicyRepository.save(new RefundPolicy(request.name(), request.active()));
        for (RefundRuleSpec rule : request.rules()) {
            refundRuleRepository.save(new RefundRule(policy, rule.minHoursBeforeShow(), rule.refundPercentage()));
        }
        return toPolicy(policy);
    }

    @Transactional(readOnly = true)
    public List<RefundPolicyResponse> listRefundPolicies() {
        return refundPolicyRepository.findAll().stream().map(this::toPolicy).toList();
    }

    // --- Mappers ------------------------------------------------------------

    private CityResponse toCity(City city) {
        return new CityResponse(city.getId(), city.getName(), city.getState());
    }

    private TheaterResponse toTheater(Theater theater) {
        return new TheaterResponse(theater.getId(), theater.getCity().getId(),
                theater.getName(), theater.getAddress());
    }

    private ScreenResponse toScreen(Screen screen) {
        return new ScreenResponse(screen.getId(), screen.getTheater().getId(), screen.getName());
    }

    private SeatResponse toSeat(Seat seat) {
        return new SeatResponse(seat.getId(), seat.getRowLabel(), seat.getSeatNumber(),
                seat.getSeatClass().getName());
    }

    private PricingTierResponse toTier(PricingTier tier) {
        return new PricingTierResponse(tier.getId(), tier.getName(), tier.getMultiplier());
    }

    private RefundPolicyResponse toPolicy(RefundPolicy policy) {
        List<RefundRuleResponse> rules = refundRuleRepository
                .findByRefundPolicy_IdOrderByMinHoursBeforeShowDesc(policy.getId()).stream()
                .map(r -> new RefundRuleResponse(r.getMinHoursBeforeShow(), r.getRefundPercentage()))
                .toList();
        return new RefundPolicyResponse(policy.getId(), policy.getName(), policy.isActive(), rules);
    }
}
