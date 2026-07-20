package com.aditya.movieticketing.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateRefundPolicyRequest(
        @NotBlank(message = "name is required") String name,
        boolean active,
        @NotEmpty(message = "rules must not be empty") List<@Valid RefundRuleSpec> rules
) {
}
