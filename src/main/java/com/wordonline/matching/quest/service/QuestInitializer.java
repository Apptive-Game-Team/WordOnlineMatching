package com.wordonline.matching.quest.service;

import com.wordonline.matching.quest.domain.QuestState;
import com.wordonline.matching.quest.entity.UserQuest;
import com.wordonline.matching.quest.repository.QuestRepository;
import com.wordonline.matching.quest.repository.UserQuestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class QuestInitializer {

    private final QuestRepository questRepository;
    private final UserQuestRepository userQuestRepository;

    @Transactional
    public Mono<Void> initializeQuests(long userId) {
        return userQuestRepository.saveAll(questRepository.findAll()
                .map(quest -> new UserQuest(null, quest.getId(), userId, QuestState.IN_PROGRESS)))
                .then();
    }
}
