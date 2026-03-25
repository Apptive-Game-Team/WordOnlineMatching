package com.wordonline.matching.decoration.repository;

import org.springframework.data.r2dbc.repository.R2dbcRepository;

import com.wordonline.matching.decoration.entity.Decoration;

@Deprecated
public interface DecorationRepository extends R2dbcRepository<Decoration, Long> {

}


