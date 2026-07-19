package com.aditya.movieticketing.web.dto;

public record ShowSeatResponse(
        Long showSeatId,
        Long seatId,
        String rowLabel,
        int seatNumber,
        String seatClass,
        String status
) {
}
