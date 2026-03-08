package com.wordonline.matching.adventure.repository;

import org.springframework.data.r2dbc.repository.R2dbcRepository;

import com.wordonline.matching.adventure.domain.Stage;

import reactor.core.publisher.Flux;

public interface StageRepository extends R2dbcRepository<Stage, Long> {

    Flux<Stage> findAllByAdventureId(Long adventureId);
}
