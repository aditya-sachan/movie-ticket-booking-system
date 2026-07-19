package com.aditya.movieticketing.web;

import com.aditya.movieticketing.service.BookingService;
import com.aditya.movieticketing.web.dto.BookingResponse;
import com.aditya.movieticketing.web.dto.CancelResponse;
import com.aditya.movieticketing.web.dto.CreateBookingRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    public BookingResponse create(@Valid @RequestBody CreateBookingRequest request,
                                  @AuthenticationPrincipal UserDetails principal) {
        return bookingService.confirm(request, principal.getUsername());
    }

    @PostMapping("/{bookingId}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public CancelResponse cancel(@PathVariable Long bookingId,
                                 @AuthenticationPrincipal UserDetails principal) {
        return bookingService.cancel(bookingId, principal.getUsername());
    }
}
