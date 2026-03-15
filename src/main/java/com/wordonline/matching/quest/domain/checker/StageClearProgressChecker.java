package com.wordonline.matching.quest.domain.checker;

import org.springframework.stereotype.Component;

import com.wordonline.matching.adventure.domain.ContentState;
import com.wordonline.matching.adventure.repository.UserStageRepository;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Component("stage_clear_pc")
@RequiredArgsConstructor
public class StageClearProgressChecker implements ProgressChecker {

    private final UserStageRepository userStageRepository;

    @Override
    public Mono<Integer> check(long userId) {
        return userStageRepository.countByUserIdAndState(userId, ContentState.FINISHED)
                .map(Long::intValue);
    }
}
