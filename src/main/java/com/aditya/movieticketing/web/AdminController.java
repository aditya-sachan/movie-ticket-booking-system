package com.aditya.movieticketing.web;

import com.aditya.movieticketing.service.AdminService;
import com.aditya.movieticketing.web.dto.CityResponse;
import com.aditya.movieticketing.web.dto.CreateCityRequest;
import com.aditya.movieticketing.web.dto.CreateShowRequest;
import com.aditya.movieticketing.web.dto.CreateTheaterRequest;
import com.aditya.movieticketing.web.dto.ShowSummaryResponse;
import com.aditya.movieticketing.web.dto.TheaterResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin management endpoints for cities, theaters, and shows. Every method requires ROLE_ADMIN.
 */
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping("/cities")
    @ResponseStatus(HttpStatus.CREATED)
    public CityResponse createCity(@Valid @RequestBody CreateCityRequest request) {
        return adminService.createCity(request);
    }

    @GetMapping("/cities")
    public List<CityResponse> listCities() {
        return adminService.listCities();
    }

    @PostMapping("/theaters")
    @ResponseStatus(HttpStatus.CREATED)
    public TheaterResponse createTheater(@Valid @RequestBody CreateTheaterRequest request) {
        return adminService.createTheater(request);
    }

    @GetMapping("/theaters")
    public List<TheaterResponse> listTheaters(@RequestParam(required = false) Long cityId) {
        return adminService.listTheaters(cityId);
    }

    @PostMapping("/shows")
    @ResponseStatus(HttpStatus.CREATED)
    public ShowSummaryResponse createShow(@Valid @RequestBody CreateShowRequest request) {
        return adminService.createShow(request);
    }
}
