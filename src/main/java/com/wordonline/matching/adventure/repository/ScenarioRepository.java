package com.wordonline.matching.adventure.repository;

import org.springframework.data.r2dbc.repository.R2dbcRepository;

import com.wordonline.matching.adventure.domain.Scenario;

import reactor.core.publisher.Flux;

public interface ScenarioRepository extends R2dbcRepository<Scenario, Long> {

    Flux<Scenario> findAllByStageId(Long stageId);
}
