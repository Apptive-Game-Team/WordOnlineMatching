package com.wordonline.matching.adventure.repository;

import org.springframework.data.r2dbc.repository.R2dbcRepository;

import com.wordonline.matching.adventure.domain.UserAdventure;

import reactor.core.publisher.Mono;

public interface UserAdventureRepository extends R2dbcRepository<UserAdventure, Long> {

    Mono<UserAdventure> findByUserIdAndAdventureId(Long userId, Long adventureId);
}
