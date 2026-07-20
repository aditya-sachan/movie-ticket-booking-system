package com.aditya.movieticketing.web.dto;

import java.util.List;

public record RefundPolicyResponse(Long id, String name, boolean active, List<RefundRuleResponse> rules) {
}
