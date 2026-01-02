package com.wordonline.matching.quest.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;

import com.wordonline.matching.quest.domain.QuestState;
import com.wordonline.matching.quest.entity.Quest;

import reactor.core.publisher.Flux;

public interface QuestRepository extends R2dbcRepository<Quest, Long> {

    @Query("""
    SELECT q FROM quests q
    JOIN user_quests uq ON q.id = uq.quest_id
    WHERE uq.user_id = :userId AND uq.state = :state
""")
    Flux<Quest> findAllByUserIdAndState(Long userId, QuestState state);
}
