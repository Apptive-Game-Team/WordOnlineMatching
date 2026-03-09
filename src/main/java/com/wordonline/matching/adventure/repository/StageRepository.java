package com.wordonline.matching.adventure.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import com.wordonline.matching.adventure.domain.Stage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface StageRepository extends R2dbcRepository<Stage, Long> {

    Flux<Stage> findAllByAdventureId(Long adventureId);

    Flux<Stage> findAllByAdventureIdOrderByIdAsc(Long adventureId);

    @Query(
            """
            SELECT st.*
            FROM stages st
            JOIN scenarios sc ON st.id = sc.stage_id
            WHERE sc.id = :scenarioId
            """
    )
    Mono<Stage> findByScenarioId(Long scenarioId);
}