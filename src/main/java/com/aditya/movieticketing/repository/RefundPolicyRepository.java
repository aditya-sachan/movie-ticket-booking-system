package com.aditya.movieticketing.repository;

import com.aditya.movieticketing.domain.RefundPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundPolicyRepository extends JpaRepository<RefundPolicy, Long> {
}
