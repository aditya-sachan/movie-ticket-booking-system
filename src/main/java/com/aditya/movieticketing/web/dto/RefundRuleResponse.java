package com.aditya.movieticketing.web.dto;

import java.math.BigDecimal;

public record RefundRuleResponse(int minHoursBeforeShow, BigDecimal refundPercentage) {
}
