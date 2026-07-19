package com.aditya.movieticketing.repository;

import com.aditya.movieticketing.domain.DiscountCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DiscountCodeRepository extends JpaRepository<DiscountCode, Long> {

    Optional<DiscountCode> findByCode(String code);

    /**
     * Locks the discount row so the redemption count can be re-checked and incremented atomically
     * at confirm time, preventing over-redemption under concurrency.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select dc from DiscountCode dc where dc.code = :code")
    Optional<DiscountCode> lockByCode(@Param("code") String code);
}
