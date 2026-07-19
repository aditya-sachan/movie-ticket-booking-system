package com.aditya.movieticketing.web.dto;

import java.math.BigDecimal;

public record PriceBreakdownResponse(
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal tax,
        BigDecimal total
) {
}
