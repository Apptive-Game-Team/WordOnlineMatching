package com.wordonline.matching.quest.repository;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import com.wordonline.matching.quest.entity.UserQuest;
import reactor.core.publisher.Mono;

public interface UserQuestRepository extends R2dbcRepository<UserQuest, Long> {
    Mono<UserQuest> findByUserIdAndQuestId(Long userId, Long questId);
}

