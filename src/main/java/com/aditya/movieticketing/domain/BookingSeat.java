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

/**
 * Link row between a booking and a booked show_seat. A partial unique index on
 * {@code show_seat_id WHERE active} is the DB-level backstop against a duplicate
 * active booking of the same seat.
 */
@Entity
@Table(name = "booking_seat")
public class BookingSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "show_seat_id", nullable = false)
    private ShowSeat showSeat;

    @Column(nullable = false)
    private boolean active = true;

    protected BookingSeat() {
    }

    public BookingSeat(Booking booking, ShowSeat showSeat) {
        this.booking = booking;
        this.showSeat = showSeat;
        this.active = true;
    }

    public Long getId() {
        return id;
    }

    public Booking getBooking() {
        return booking;
    }

    public ShowSeat getShowSeat() {
        return showSeat;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
