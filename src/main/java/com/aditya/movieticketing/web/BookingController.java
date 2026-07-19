package com.aditya.movieticketing.web;

import com.aditya.movieticketing.service.BookingService;
import com.aditya.movieticketing.web.dto.BookingResponse;
import com.aditya.movieticketing.web.dto.CancelBookingRequest;
import com.aditya.movieticketing.web.dto.CancelResponse;
import com.aditya.movieticketing.web.dto.CreateBookingRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
    public BookingResponse create(@Valid @RequestBody CreateBookingRequest request) {
        return bookingService.confirm(request);
    }

    @PostMapping("/{bookingId}/cancel")
    public CancelResponse cancel(@PathVariable Long bookingId,
                                 @Valid @RequestBody CancelBookingRequest request) {
        return bookingService.cancel(bookingId, request.userId());
    }
}
