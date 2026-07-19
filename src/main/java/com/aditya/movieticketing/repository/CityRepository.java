package com.aditya.movieticketing.repository;

import com.aditya.movieticketing.domain.City;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CityRepository extends JpaRepository<City, Long> {
}
