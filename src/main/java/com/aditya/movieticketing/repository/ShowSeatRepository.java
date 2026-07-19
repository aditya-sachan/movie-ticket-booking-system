package com.aditya.movieticketing.repository;

import com.aditya.movieticketing.domain.SeatStatus;
import com.aditya.movieticketing.domain.ShowSeat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {

    /**
     * Locks the requested seats of a show with SELECT ... FOR UPDATE, ordered by seat id.
     * Consistent lock-acquisition order across concurrent holds prevents deadlock. This is the
     * heart of the no-double-booking guarantee: a second transaction requesting an overlapping
     * seat blocks here until the first commits, then observes the updated status.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select ss from ShowSeat ss
            where ss.show.id = :showId and ss.seat.id in :seatIds
            order by ss.seat.id
            """)
    List<ShowSeat> lockSeatsForHold(@Param("showId") Long showId,
                                    @Param("seatIds") Collection<Long> seatIds);

    /**
     * Locks all seats currently held under a given hold token, ordered by seat id, for confirm.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select ss from ShowSeat ss
            where ss.show.id = :showId and ss.holdToken = :holdToken
            order by ss.seat.id
            """)
    List<ShowSeat> lockSeatsByHoldToken(@Param("showId") Long showId,
                                        @Param("holdToken") UUID holdToken);

    /**
     * Backstop cleanup run by the @Scheduled sweep: release HELD seats whose hold has expired.
     * Holds also reclaim their own expired rows on demand, so this only mops up abandoned holds.
     */
    @Modifying
    @Query("""
            update ShowSeat ss
            set ss.status = :available, ss.holdToken = null, ss.holdExpiresAt = null
            where ss.status = :held and ss.holdExpiresAt < :now
            """)
    int releaseExpiredHolds(@Param("now") Instant now,
                            @Param("available") SeatStatus available,
                            @Param("held") SeatStatus held);

    @Query("""
            select ss from ShowSeat ss
            join fetch ss.seat s
            where ss.show.id = :showId
            order by s.id
            """)
    List<ShowSeat> findByShowWithSeat(@Param("showId") Long showId);
}
