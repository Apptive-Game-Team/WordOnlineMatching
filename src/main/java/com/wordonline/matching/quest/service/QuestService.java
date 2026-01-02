package com.wordonline.matching.quest.service;

import org.springframework.stereotype.Service;

import com.wordonline.matching.quest.domain.QuestState;
import com.wordonline.matching.quest.repository.QuestRepository;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class QuestService {

    private final QuestRepository questRepository;
    private final QuestChecker questChecker;
    private final QuestRewardGiver rewardGiver;

    public Mono<Void> checkQuests(long userId) {
        return questRepository.findAllByUserIdAndState(userId, QuestState.IN_PROGRESS)
                .filterWhen(quest -> questChecker.check(userId, quest))
                .doOnNext(quest -> rewardGiver.give(userId, quest))
                .then();
    }
}
