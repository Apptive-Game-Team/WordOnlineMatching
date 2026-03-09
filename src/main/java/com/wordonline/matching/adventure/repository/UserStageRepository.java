package com.wordonline.matching.adventure.repository;

import com.wordonline.matching.adventure.domain.ContentState;
import com.wordonline.matching.adventure.domain.UserStage;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserStageRepository extends R2dbcRepository<UserStage, Long> {
    Mono<UserStage> findByUserIdAndStageId(Long userId, Long stageId);

    Mono<Long> countByUserIdAndState(Long userId, ContentState state);
    Flux<UserStage> findAllByUserId(long userId);
}