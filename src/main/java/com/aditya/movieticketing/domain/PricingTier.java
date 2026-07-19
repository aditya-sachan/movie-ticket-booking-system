package com.aditya.movieticketing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "pricing_tier")
public class PricingTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String name;

    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal multiplier;

    protected PricingTier() {
    }

    public PricingTier(String name, BigDecimal multiplier) {
        this.name = name;
        this.multiplier = multiplier;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getMultiplier() {
        return multiplier;
    }
}
