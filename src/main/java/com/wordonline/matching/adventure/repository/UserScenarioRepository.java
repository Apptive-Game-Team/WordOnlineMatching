package com.wordonline.matching.adventure.repository;

import javax.swing.plaf.nimbus.State;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;

import com.wordonline.matching.adventure.domain.UserScenario;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserScenarioRepository extends R2dbcRepository<UserScenario, Long> {

    Mono<UserScenario> findByUserIdAndScenarioId(Long userId, Long scenarioId);

    @Query(
            """
            INSERT INTO user_scenarios(user_id, scenario_id, state)
                (
                    SELECT :userId, MIN(sc.id), 'ACTIVE'
                    FROM scenarios sc
                        JOIN stages s ON sc.stage_id = s.id
                        JOIN adventures a ON a.id = s.adventure_id
                    WHERE a.access_type = 'FREE'
                    GROUP BY s.adventure_id
                )
            ON CONFLICT (user_id, scenario_id)
            DO UPDATE SET state ='ACTIVE'
            """
    )
    Mono<Void> updateStateActiveFreeAdventure(Long userId);

    @Query(
            """
            WITH scenario_flow AS (
                SELECT
                    s.id AS scenario_id,
                    s.stage_id,
                    st.adventure_id,
                    LAG(us.state) OVER (PARTITION BY st.adventure_id ORDER BY s.id) as prev_state,
                    us.state as current_state
                FROM scenarios s
                JOIN stages st ON s.stage_id = st.id
                LEFT JOIN user_scenarios us ON us.scenario_id = s.id AND us.user_id = :userId
                WHERE st.adventure_id = :adventureId
            )
            INSERT INTO user_scenarios (user_id, scenario_id, state)
            SELECT
                :userId,
                sf.scenario_id,
                'ACTIVE'
            FROM scenario_flow sf
            WHERE
                (sf.current_state IS NULL OR sf.current_state = 'INACTIVE')
                AND (
                    sf.prev_state = 'FINISHED'
                    OR sf.prev_state IS NULL
                )
            ON CONFLICT (user_id, scenario_id)
            DO UPDATE SET
                state = 'ACTIVE'
            WHERE user_scenarios.state = 'INACTIVE';
            """
    )
    Mono<Void> updateStateActiveWhenBeforeScenarioIsFinished(Long user);

    @Query(
            """
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
            """
    )
    Flux<AdventureProjection> findAllAdventureProgress(Long userId);

    @Query(
            """
            SELECT COUNT(*)
            FROM (
                SELECT st.id
                FROM stages st
                JOIN scenarios sc ON st.id = sc.stage_id
                JOIN user_scenarios us ON sc.id = us.scenario_id
                WHERE us.user_id = :userId
                GROUP BY st.id
                HAVING COUNT(sc.id) = COUNT(CASE WHEN us.state = 'FINISHED' THEN 1 END)
            ) AS finished_stages
            """
    )
    Mono<Long> countFinishedStageByUserId(Long userId);
}
