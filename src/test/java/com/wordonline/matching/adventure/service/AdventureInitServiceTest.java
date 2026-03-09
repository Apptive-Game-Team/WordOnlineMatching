package com.wordonline.matching.adventure.service;

import com.wordonline.matching.adventure.domain.AccessType;
import com.wordonline.matching.adventure.domain.Adventure;
import com.wordonline.matching.adventure.domain.ContentState;
import com.wordonline.matching.adventure.domain.Scenario;
import com.wordonline.matching.adventure.domain.Stage;
import com.wordonline.matching.adventure.domain.UserAdventure;
import com.wordonline.matching.adventure.domain.UserScenario;
import com.wordonline.matching.adventure.repository.AdventureRepository;
import com.wordonline.matching.adventure.repository.ScenarioRepository;
import com.wordonline.matching.adventure.repository.StageRepository;
import com.wordonline.matching.adventure.repository.UserAdventureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdventureInitServiceTest {

    @Mock
    private AdventureRepository adventureRepository;
    @Mock
    private StageRepository stageRepository;
    @Mock
    private ScenarioRepository scenarioRepository;
    @Mock
    private UserAdventureRepository userAdventureRepository;
    @Mock
    private UserDataService userDataService;

    @InjectMocks
    private AdventureInitService adventureInitService;

    private Adventure freeAdventure;
    private Adventure paidAdventure;
    private Stage firstStage;
    private Scenario firstScenario;

    @BeforeEach
    void setUp() {
        freeAdventure = new Adventure(1L, AccessType.FREE);
        paidAdventure = new Adventure(2L, AccessType.PAID);
        firstStage = new Stage(1L, 1L);
        firstScenario = new Scenario(1L, 1L);
    }

    @Test
    @DisplayName("무료_어드벤처_활성화_성공")
    void activateFreeAdventures_NewUser_ActivatesFreeAdventure() {
        long userId = 1L;

        when(adventureRepository.findAll()).thenReturn(Flux.just(freeAdventure, paidAdventure));
        when(userAdventureRepository.findByUserIdAndAdventureId(userId, freeAdventure.getId())).thenReturn(Mono.empty());
        when(userDataService.saveUserAdventure(userId, freeAdventure.getId(), ContentState.ACTIVE)).thenReturn(Mono.empty());
        when(stageRepository.findAllByAdventureIdOrderByIdAsc(freeAdventure.getId())).thenReturn(Flux.just(firstStage));
        when(userDataService.saveUserStage(userId, firstStage.getId(), ContentState.ACTIVE)).thenReturn(Mono.empty());
        when(scenarioRepository.findAllByStageIdOrderByIdAsc(firstStage.getId())).thenReturn(Flux.just(firstScenario));
        when(userDataService.saveUserScenario(userId, firstScenario.getId(), ContentState.ACTIVE)).thenReturn(Mono.just(new UserScenario()));

        Mono<Void> result = adventureInitService.activateFreeAdventures(userId);

        StepVerifier.create(result)
                .verifyComplete();

        verify(userDataService).saveUserAdventure(userId, freeAdventure.getId(), ContentState.ACTIVE);
        verify(userDataService).saveUserStage(userId, firstStage.getId(), ContentState.ACTIVE);
        verify(userDataService).saveUserScenario(userId, firstScenario.getId(), ContentState.ACTIVE);
    }

    @Test
    @DisplayName("무료_어드벤처_활성화_이미_활성화된_경우_동작_안함")
    void activateFreeAdventures_AlreadyActive_DoesNothing() {
        long userId = 1L;
        UserAdventure activeUserAdventure = new UserAdventure(1L, userId, freeAdventure.getId(), ContentState.ACTIVE);

        when(adventureRepository.findAll()).thenReturn(Flux.just(freeAdventure, paidAdventure));
        when(userAdventureRepository.findByUserIdAndAdventureId(userId, freeAdventure.getId())).thenReturn(Mono.just(activeUserAdventure));

        Mono<Void> result = adventureInitService.activateFreeAdventures(userId);

        StepVerifier.create(result)
                .verifyComplete();

        verify(userDataService, never()).saveUserAdventure(anyLong(), anyLong(), any());
    }
}
