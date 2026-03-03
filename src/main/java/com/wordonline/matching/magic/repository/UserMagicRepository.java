package com.wordonline.matching.magic.repository;

import org.springframework.data.r2dbc.repository.R2dbcRepository;

import com.wordonline.matching.magic.domain.UserMagic;

import reactor.core.publisher.Flux;

public interface UserMagicRepository extends R2dbcRepository<UserMagic, Long> {

    Flux<UserMagic> findAllByUserId(long userId);
}
