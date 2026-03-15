package com.wordonline.matching.data.repository;

import com.wordonline.matching.data.entity.Parameter;
import org.springframework.data.r2dbc.repository.R2dbcRepository;

public interface ParameterRepository extends R2dbcRepository<Parameter, Long> {
}
