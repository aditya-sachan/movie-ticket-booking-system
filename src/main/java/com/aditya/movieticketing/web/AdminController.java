package com.aditya.movieticketing.web;

import com.aditya.movieticketing.service.AdminService;
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
import com.aditya.movieticketing.web.dto.ScreenResponse;
import com.aditya.movieticketing.web.dto.SeatResponse;
import com.aditya.movieticketing.web.dto.ShowSummaryResponse;
import com.aditya.movieticketing.web.dto.TheaterResponse;
import com.aditya.movieticketing.web.dto.UpdatePricingTierRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin management endpoints for cities, theaters, seat layouts (screens + seats), shows, pricing
 * tiers, and refund policies. Every method requires ROLE_ADMIN.
 */
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // --- Cities ---
    @PostMapping("/cities")
    @ResponseStatus(HttpStatus.CREATED)
    public CityResponse createCity(@Valid @RequestBody CreateCityRequest request) {
        return adminService.createCity(request);
    }

    @GetMapping("/cities")
    public List<CityResponse> listCities() {
        return adminService.listCities();
    }

    // --- Theaters ---
    @PostMapping("/theaters")
    @ResponseStatus(HttpStatus.CREATED)
    public TheaterResponse createTheater(@Valid @RequestBody CreateTheaterRequest request) {
        return adminService.createTheater(request);
    }

    @GetMapping("/theaters")
    public List<TheaterResponse> listTheaters(@RequestParam(required = false) Long cityId) {
        return adminService.listTheaters(cityId);
    }

    // --- Seat layouts: screens + seats ---
    @PostMapping("/screens")
    @ResponseStatus(HttpStatus.CREATED)
    public ScreenResponse createScreen(@Valid @RequestBody CreateScreenRequest request) {
        return adminService.createScreen(request);
    }

    @GetMapping("/screens")
    public List<ScreenResponse> listScreens(@RequestParam(required = false) Long theaterId) {
        return adminService.listScreens(theaterId);
    }

    @PostMapping("/screens/{screenId}/seats")
    @ResponseStatus(HttpStatus.CREATED)
    public List<SeatResponse> addSeats(@PathVariable Long screenId, @Valid @RequestBody CreateSeatsRequest request) {
        return adminService.addSeats(screenId, request);
    }

    @GetMapping("/screens/{screenId}/seats")
    public List<SeatResponse> listSeats(@PathVariable Long screenId) {
        return adminService.listSeats(screenId);
    }

    // --- Shows ---
    @PostMapping("/shows")
    @ResponseStatus(HttpStatus.CREATED)
    public ShowSummaryResponse createShow(@Valid @RequestBody CreateShowRequest request) {
        return adminService.createShow(request);
    }

    // --- Pricing tiers ---
    @PostMapping("/pricing-tiers")
    @ResponseStatus(HttpStatus.CREATED)
    public PricingTierResponse createPricingTier(@Valid @RequestBody CreatePricingTierRequest request) {
        return adminService.createPricingTier(request);
    }

    @GetMapping("/pricing-tiers")
    public List<PricingTierResponse> listPricingTiers() {
        return adminService.listPricingTiers();
    }

    @PutMapping("/pricing-tiers/{id}")
    public PricingTierResponse updatePricingTier(@PathVariable Long id,
                                                 @Valid @RequestBody UpdatePricingTierRequest request) {
        return adminService.updatePricingTier(id, request);
    }

    // --- Refund policies ---
    @PostMapping("/refund-policies")
    @ResponseStatus(HttpStatus.CREATED)
    public RefundPolicyResponse createRefundPolicy(@Valid @RequestBody CreateRefundPolicyRequest request) {
        return adminService.createRefundPolicy(request);
    }

    @GetMapping("/refund-policies")
    public List<RefundPolicyResponse> listRefundPolicies() {
        return adminService.listRefundPolicies();
    }
}
