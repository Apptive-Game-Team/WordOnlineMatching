package com.wordonline.matching.adventure.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;

import com.wordonline.matching.adventure.domain.UserScenario;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserScenarioRepository extends R2dbcRepository<UserScenario, Long> {

    Mono<UserScenario> findByUserIdAndScenarioId(Long userId, Long scenarioId);

    @Query(
            """
            SELECT us.*
            FROM user_scenarios us
            JOIN scenarios sc ON us.scenario_id = sc.id
            JOIN stages st ON sc.stage_id = st.id
            JOIN adventures a ON st.adventure_id = a.id
            WHERE a.id = :adventureId
            AND us.user_id = :userId
            ORDER BY st.id, sc.id
            """
    )
    Flux<UserScenario> findAllByUserIdAndAdventureIdOrderByStageIdAndScenarioId(Long userId, Long adventureId);
}
