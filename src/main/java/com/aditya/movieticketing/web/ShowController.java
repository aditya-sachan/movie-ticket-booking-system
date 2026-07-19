package com.aditya.movieticketing.web;

import com.aditya.movieticketing.service.HoldService;
import com.aditya.movieticketing.service.ShowQueryService;
import com.aditya.movieticketing.web.dto.HoldRequest;
import com.aditya.movieticketing.web.dto.HoldResponse;
import com.aditya.movieticketing.web.dto.ShowSeatResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/shows")
public class ShowController {

    private final ShowQueryService showQueryService;
    private final HoldService holdService;

    public ShowController(ShowQueryService showQueryService, HoldService holdService) {
        this.showQueryService = showQueryService;
        this.holdService = holdService;
    }

    @GetMapping("/{showId}/seats")
    @PreAuthorize("isAuthenticated()")
    public List<ShowSeatResponse> seats(@PathVariable Long showId) {
        return showQueryService.seatMap(showId);
    }

    @PostMapping("/{showId}/holds")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    public HoldResponse hold(@PathVariable Long showId, @Valid @RequestBody HoldRequest request) {
        return holdService.hold(showId, request.seatIds(), request.discountCode());
    }
}
