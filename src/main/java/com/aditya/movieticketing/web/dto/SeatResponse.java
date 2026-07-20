package com.aditya.movieticketing.web.dto;

public record SeatResponse(Long id, String rowLabel, int seatNumber, String seatClass) {
}
