package com.wordonline.matching.adventure.repository;

import org.springframework.data.r2dbc.repository.R2dbcRepository;

import com.wordonline.matching.adventure.domain.UserScenario;

import reactor.core.publisher.Mono;

public interface UserScenarioRepository extends R2dbcRepository<UserScenario, Long> {

    Mono<UserScenario> findByUserIdAndScenarioId(Long userId, Long scenarioId);
}
