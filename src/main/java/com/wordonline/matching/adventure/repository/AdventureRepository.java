package com.wordonline.matching.adventure.repository;

import org.springframework.data.r2dbc.repository.R2dbcRepository;

import com.wordonline.matching.adventure.domain.Adventure;

public interface AdventureRepository extends R2dbcRepository<Adventure, Long> {
}
