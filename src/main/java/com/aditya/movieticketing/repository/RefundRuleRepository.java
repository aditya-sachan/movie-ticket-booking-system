package com.aditya.movieticketing.repository;

import com.aditya.movieticketing.domain.RefundRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RefundRuleRepository extends JpaRepository<RefundRule, Long> {

    List<RefundRule> findByRefundPolicy_IdOrderByMinHoursBeforeShowDesc(Long refundPolicyId);
}
