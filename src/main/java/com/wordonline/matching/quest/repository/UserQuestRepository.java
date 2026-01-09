package com.wordonline.matching.quest.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import com.wordonline.matching.quest.entity.UserQuest;
import reactor.core.publisher.Mono;

public interface UserQuestRepository extends R2dbcRepository<UserQuest, Long> {

    Mono<UserQuest> findByUserIdAndQuestId(Long userId, Long questId);

    @Query("""
UPDATE user_quests
SET state = 'COMPLETED'
WHERE user_quests.user_id=:userId AND user_quests.quest_id=:questId
""")
    Mono<Void> setCompletedByUserIdAndQuestId(Long userId, Long questId);
}

