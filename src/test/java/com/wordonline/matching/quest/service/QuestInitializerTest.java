package com.wordonline.matching.quest.service;

import com.wordonline.matching.quest.domain.QuestState;
import com.wordonline.matching.quest.entity.Quest;
import com.wordonline.matching.quest.entity.UserQuest;
import com.wordonline.matching.quest.repository.QuestRepository;
import com.wordonline.matching.quest.repository.UserQuestRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestInitializerTest {

    @Mock
    private QuestRepository questRepository;

    @Mock
    private UserQuestRepository userQuestRepository;

    @InjectMocks
    private QuestInitializer questInitializer;

    @Captor
    private ArgumentCaptor<UserQuest> userQuestArgumentCaptor;

    @Test
    @DisplayName("퀘스트_초기화_성공")
    void initializeQuests_Success() {
        long userId = 1L;
        Quest quest1 = new Quest(1L, "checker1", 10, "giver1");
        Quest quest2 = new Quest(2L, "checker2", 20, "giver2");
        List<Quest> quests = List.of(quest1, quest2);

        when(questRepository.findAll()).thenReturn(Flux.fromIterable(quests));
        when(userQuestRepository.save(any(UserQuest.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        Mono<Void> result = questInitializer.initializeQuests(userId);

        StepVerifier.create(result).verifyComplete();

        verify(userQuestRepository, times(2)).save(userQuestArgumentCaptor.capture());
        List<UserQuest> savedUserQuests = userQuestArgumentCaptor.getAllValues();

        assertEquals(2, savedUserQuests.size());
        assertEquals(userId, savedUserQuests.get(0).getUserId());
        assertEquals(quest1.getId(), savedUserQuests.get(0).getQuestId());
        assertEquals(QuestState.IN_PROGRESS, savedUserQuests.get(0).getState());
        assertEquals(userId, savedUserQuests.get(1).getUserId());
        assertEquals(quest2.getId(), savedUserQuests.get(1).getQuestId());
        assertEquals(QuestState.IN_PROGRESS, savedUserQuests.get(1).getState());
    }
}
