package com.aditya.movieticketing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "movie")
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 60)
    private String language;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(length = 20)
    private String certification;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Movie() {
    }

    public Movie(String title, String language, int durationMinutes, String certification) {
        this.title = title;
        this.language = language;
        this.durationMinutes = durationMinutes;
        this.certification = certification;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getLanguage() {
        return language;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public String getCertification() {
        return certification;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
