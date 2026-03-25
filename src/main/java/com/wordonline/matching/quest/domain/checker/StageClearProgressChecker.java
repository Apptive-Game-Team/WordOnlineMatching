package com.wordonline.matching.quest.domain.checker;

import com.wordonline.matching.adventure.repository.UserScenarioRepository;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Component("stage_clear_pc")
@RequiredArgsConstructor
public class StageClearProgressChecker implements ProgressChecker {

    private final UserScenarioRepository userScenarioRepository;

    @Override
    public Mono<Integer> check(long userId) {
        return userScenarioRepository.countFinishedStageByUserId(userId)
                .map(countDto -> countDto.getCount().intValue());
    }
}
