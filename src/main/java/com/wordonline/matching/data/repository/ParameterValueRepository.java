package com.wordonline.matching.data.repository;

import com.wordonline.matching.data.entity.ParameterValue;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;

public interface ParameterValueRepository extends R2dbcRepository<ParameterValue, Long> {

    @Query("""
        SELECT
            pv.*
        FROM
            parameter_values pv
        WHERE
            pv.game_object_id IN (
                SELECT DISTINCT updated.game_object_id
                FROM parameter_values updated
                WHERE updated.updated_at > :timestamp
            )
    """)
    Flux<ParameterValue> findAllUpdatedSince(LocalDateTime timestamp);

    @Query("""
        SELECT
            pv.*
        FROM
            parameter_values pv
    """)
    Flux<ParameterValue> findAllParameters();
}
