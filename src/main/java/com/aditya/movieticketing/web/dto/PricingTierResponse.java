package com.aditya.movieticketing.web.dto;

import java.math.BigDecimal;

public record PricingTierResponse(Long id, String name, BigDecimal multiplier) {
}
