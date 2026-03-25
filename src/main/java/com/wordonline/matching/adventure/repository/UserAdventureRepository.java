package com.wordonline.matching.adventure.repository;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
@Repository
public class UserAdventureRepository {

    private final DatabaseClient databaseClient;

    public Flux<AdventureDatabaseDto> findAllAdventureProgress(Long userId) {
        String sql = """
            SELECT
                a.id AS adventure_id,
                CASE
                    WHEN COUNT(DISTINCT st.id) = COUNT(DISTINCT CASE WHEN us_agg.stage_finished THEN st.id END) THEN 'FINISHED'
                    WHEN COUNT(DISTINCT CASE WHEN us_agg.stage_active OR us_agg.stage_finished THEN st.id END) > 0 THEN 'ACTIVE'
                    ELSE 'INACTIVE'
                END AS adventure_state,
                st.id AS stage_id,
                CASE
                    WHEN COUNT(sc.id) = COUNT(CASE WHEN usc.state = 'FINISHED' THEN sc.id END) THEN 'FINISHED'
                    WHEN COUNT(CASE WHEN usc.state = 'ACTIVE' OR usc.state = 'FINISHED' THEN sc.id END) > 0 THEN 'ACTIVE'
                    ELSE 'INACTIVE'
                END AS stage_state,
                sc.id AS scenario_id,
                COALESCE(usc.state, 'INACTIVE') AS scenario_state
            FROM adventures a
            JOIN stages st ON a.id = st.adventure_id
            JOIN scenarios sc ON st.id = sc.stage_id
            LEFT JOIN user_scenarios usc ON sc.id = usc.scenario_id AND usc.user_id = :userId
            LEFT JOIN LATERAL (
                SELECT
                    bool_and(usc2.state = 'FINISHED') as stage_finished,
                    bool_or(usc2.state = 'ACTIVE') as stage_active
                FROM scenarios sc2
                LEFT JOIN user_scenarios usc2 ON sc2.id = usc2.scenario_id AND usc2.user_id = :userId
                WHERE sc2.stage_id = st.id
            ) us_agg ON TRUE
            GROUP BY a.id, st.id, sc.id, usc.state
            ORDER BY a.id, st.id, sc.id
            """;

        return databaseClient.sql(sql)
                .bind("userId", userId)
                .map((row, metadata) -> new AdventureDatabaseDto(
                        row.get("adventure_id", Long.class),
                        row.get("adventure_state", String.class),
                        row.get("stage_id", Long.class),
                        row.get("stage_state", String.class),
                        row.get("scenario_id", Long.class),
                        row.get("scenario_state", String.class)
                ))
                .all()
                .doOnNext(dto -> System.out.println("Mapped DTO: " + dto)); // 여기서 데이터 확인
    }
}
