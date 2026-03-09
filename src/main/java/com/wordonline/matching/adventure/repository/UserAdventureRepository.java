package com.wordonline.matching.adventure.repository;

import com.wordonline.matching.adventure.domain.UserAdventure;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserAdventureRepository extends R2dbcRepository<UserAdventure, Long> {

    Mono<UserAdventure> findByUserIdAndAdventureId(Long userId, Long adventureId);

    Flux<UserAdventure> findAllByUserId(long userId);
}