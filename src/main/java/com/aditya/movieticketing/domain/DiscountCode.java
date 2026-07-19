package com.aditya.movieticketing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "discount_code")
public class DiscountCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal value;

    @Column(name = "min_order_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal minOrderValue = BigDecimal.ZERO;

    @Column(name = "max_redemptions")
    private Integer maxRedemptions;

    @Column(name = "times_redeemed", nullable = false)
    private int timesRedeemed = 0;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected DiscountCode() {
    }

    public DiscountCode(String code, DiscountType discountType, BigDecimal value, BigDecimal minOrderValue,
                        Integer maxRedemptions, Instant validFrom, Instant validUntil, boolean active) {
        this.code = code;
        this.discountType = discountType;
        this.value = value;
        this.minOrderValue = minOrderValue;
        this.maxRedemptions = maxRedemptions;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    public BigDecimal getValue() {
        return value;
    }

    public BigDecimal getMinOrderValue() {
        return minOrderValue;
    }

    public Integer getMaxRedemptions() {
        return maxRedemptions;
    }

    public int getTimesRedeemed() {
        return timesRedeemed;
    }

    public void setTimesRedeemed(int timesRedeemed) {
        this.timesRedeemed = timesRedeemed;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidUntil() {
        return validUntil;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
