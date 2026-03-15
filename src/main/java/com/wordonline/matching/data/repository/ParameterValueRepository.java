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
            pv.updated_at > :timestamp
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
