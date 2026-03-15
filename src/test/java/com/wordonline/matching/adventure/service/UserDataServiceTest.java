package com.wordonline.matching.adventure.service;

import com.wordonline.matching.adventure.domain.ContentState;
import com.wordonline.matching.adventure.domain.UserAdventure;
import com.wordonline.matching.adventure.domain.UserScenario;
import com.wordonline.matching.adventure.domain.UserStage;
import com.wordonline.matching.adventure.repository.UserAdventureRepository;
import com.wordonline.matching.adventure.repository.UserScenarioRepository;
import com.wordonline.matching.adventure.repository.UserStageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDataServiceTest {

    @Mock
    private UserAdventureRepository userAdventureRepository;
    @Mock
    private UserStageRepository userStageRepository;
    @Mock
    private UserScenarioRepository userScenarioRepository;

    @InjectMocks
    private UserDataService userDataService;

    @Test
    @DisplayName("유저_시나리오_저장_성공")
    void saveUserScenario_Success() {
        long userId = 1L;
        long scenarioId = 1L;
        UserScenario scenario = new UserScenario(1L, userId, scenarioId, ContentState.ACTIVE);

        when(userScenarioRepository.findByUserIdAndScenarioId(anyLong(), anyLong())).thenReturn(Mono.empty());
        when(userScenarioRepository.save(any(UserScenario.class))).thenReturn(Mono.just(scenario));

        Mono<UserScenario> result = userDataService.saveUserScenario(userId, scenarioId, ContentState.ACTIVE);

        StepVerifier.create(result)
                .expectNext(scenario)
                .verifyComplete();

        verify(userScenarioRepository).save(any(UserScenario.class));
    }

    @Test
    @DisplayName("유저_스테이지_저장_성공")
    void saveUserStage_Success() {
        long userId = 1L;
        long stageId = 1L;
        UserStage stage = new UserStage(1L, userId, stageId, ContentState.ACTIVE);

        when(userStageRepository.findByUserIdAndStageId(anyLong(), anyLong())).thenReturn(Mono.empty());
        when(userStageRepository.save(any(UserStage.class))).thenReturn(Mono.just(stage));

        Mono<Void> result = userDataService.saveUserStage(userId, stageId, ContentState.ACTIVE);

        StepVerifier.create(result)
                .verifyComplete();

        verify(userStageRepository).save(any(UserStage.class));
    }

    @Test
    @DisplayName("유저_어드벤처_저장_성공")
    void saveUserAdventure_Success() {
        long userId = 1L;
        long adventureId = 1L;
        UserAdventure adventure = new UserAdventure(1L, userId, adventureId, ContentState.ACTIVE);

        when(userAdventureRepository.findByUserIdAndAdventureId(anyLong(), anyLong())).thenReturn(Mono.empty());
        when(userAdventureRepository.save(any(UserAdventure.class))).thenReturn(Mono.just(adventure));

        Mono<Void> result = userDataService.saveUserAdventure(userId, adventureId, ContentState.ACTIVE);

        StepVerifier.create(result)
                .verifyComplete();

        verify(userAdventureRepository).save(any(UserAdventure.class));
    }
}
