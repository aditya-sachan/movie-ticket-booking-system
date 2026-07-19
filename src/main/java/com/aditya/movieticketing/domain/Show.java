package com.aditya.movieticketing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A scheduled show of a movie on a screen. Mapped to table {@code movie_show}
 * to avoid the SQL {@code SHOW} keyword.
 */
@Entity
@Table(name = "movie_show")
public class Show {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "screen_id", nullable = false)
    private Screen screen;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pricing_tier_id", nullable = false)
    private PricingTier pricingTier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_policy_id")
    private RefundPolicy refundPolicy;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "base_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal basePrice;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Show() {
    }

    public Show(Movie movie, Screen screen, PricingTier pricingTier, RefundPolicy refundPolicy,
                Instant startsAt, Instant endsAt, BigDecimal basePrice) {
        this.movie = movie;
        this.screen = screen;
        this.pricingTier = pricingTier;
        this.refundPolicy = refundPolicy;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.basePrice = basePrice;
    }

    public Long getId() {
        return id;
    }

    public Movie getMovie() {
        return movie;
    }

    public Screen getScreen() {
        return screen;
    }

    public PricingTier getPricingTier() {
        return pricingTier;
    }

    public RefundPolicy getRefundPolicy() {
        return refundPolicy;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
