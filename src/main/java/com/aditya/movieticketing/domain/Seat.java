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

import java.time.Instant;

@Entity
@Table(name = "seat")
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "screen_id", nullable = false)
    private Screen screen;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_class_id", nullable = false)
    private SeatClass seatClass;

    @Column(name = "row_label", nullable = false, length = 4)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false)
    private int seatNumber;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Seat() {
    }

    public Seat(Screen screen, SeatClass seatClass, String rowLabel, int seatNumber) {
        this.screen = screen;
        this.seatClass = seatClass;
        this.rowLabel = rowLabel;
        this.seatNumber = seatNumber;
    }

    public Long getId() {
        return id;
    }

    public Screen getScreen() {
        return screen;
    }

    public SeatClass getSeatClass() {
        return seatClass;
    }

    public String getRowLabel() {
        return rowLabel;
    }

    public int getSeatNumber() {
        return seatNumber;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
