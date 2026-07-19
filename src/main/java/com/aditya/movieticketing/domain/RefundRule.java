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

@Entity
@Table(name = "refund_rule")
public class RefundRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "refund_policy_id", nullable = false)
    private RefundPolicy refundPolicy;

    @Column(name = "min_hours_before_show", nullable = false)
    private int minHoursBeforeShow;

    @Column(name = "refund_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal refundPercentage;

    protected RefundRule() {
    }

    public RefundRule(RefundPolicy refundPolicy, int minHoursBeforeShow, BigDecimal refundPercentage) {
        this.refundPolicy = refundPolicy;
        this.minHoursBeforeShow = minHoursBeforeShow;
        this.refundPercentage = refundPercentage;
    }

    public Long getId() {
        return id;
    }

    public RefundPolicy getRefundPolicy() {
        return refundPolicy;
    }

    public int getMinHoursBeforeShow() {
        return minHoursBeforeShow;
    }

    public BigDecimal getRefundPercentage() {
        return refundPercentage;
    }
}
