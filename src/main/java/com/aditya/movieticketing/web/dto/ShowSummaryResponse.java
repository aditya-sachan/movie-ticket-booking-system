package com.aditya.movieticketing.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ShowSummaryResponse(
        Long showId,
        Long movieId,
        String movieTitle,
        String language,
        Long theaterId,
        String theaterName,
        String cityName,
        String screenName,
        String pricingTier,
        Instant startsAt,
        Instant endsAt,
        BigDecimal basePrice
) {
}
