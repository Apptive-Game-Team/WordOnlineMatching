package com.wordonline.matching.adventure.service;

import com.wordonline.matching.adventure.domain.Adventure;
import com.wordonline.matching.adventure.domain.ContentState;
import com.wordonline.matching.adventure.domain.Scenario;
import com.wordonline.matching.adventure.domain.Stage;
import com.wordonline.matching.adventure.domain.UserAdventure;
import com.wordonline.matching.adventure.domain.UserScenario;
import com.wordonline.matching.adventure.domain.UserStage;
import com.wordonline.matching.adventure.dto.AdventuresResponse;
import com.wordonline.matching.adventure.repository.AdventureRepository;
import com.wordonline.matching.adventure.repository.ScenarioRepository;
import com.wordonline.matching.adventure.repository.StageRepository;
import com.wordonline.matching.adventure.repository.UserAdventureRepository;
import com.wordonline.matching.adventure.repository.UserScenarioRepository;
import com.wordonline.matching.adventure.repository.UserStageRepository;
import com.wordonline.matching.quest.service.QuestService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdventureServiceTest {

    // Repositories are still needed for DTO building
    @Mock
    private AdventureRepository adventureRepository;
    @Mock
    private StageRepository stageRepository;
    @Mock
    private ScenarioRepository scenarioRepository;
    @Mock
    private UserAdventureRepository userAdventureRepository;
    @Mock
    private UserStageRepository userStageRepository;
    @Mock
    private UserScenarioRepository userScenarioRepository;
    
    // Mocks for new services
    @Mock
    private QuestService questService;
    @Mock
    private AdventureInitService adventureInitService;
    @Mock
    private AdventureProgressService adventureProgressService;
    @Mock
    private UserDataService userDataService;

    @InjectMocks
    private AdventureService adventureService;

    private Adventure adventure;
    private Stage stage;
    private Scenario scenario;

    @BeforeEach
    void setUp() {
        adventure = new Adventure(1L, null); // AccessType can be null for tests not focused on it
        stage = new Stage(1L, 1L);
        scenario = new Scenario(1L, 1L);
    }

    @Test
    @DisplayName("모험_목록_조회_성공")
    void getAdventures_ReturnsCorrectStates() {
        long userId = 1L;

        when(adventureRepository.findAll()).thenReturn(Flux.just(adventure));
        when(userAdventureRepository.findByUserIdAndAdventureId(userId, adventure.getId()))
                .thenReturn(Mono.just(new UserAdventure(1L, userId, adventure.getId(), ContentState.ACTIVE)));
        when(stageRepository.findAllByAdventureId(adventure.getId())).thenReturn(Flux.just(stage));
        when(userStageRepository.findByUserIdAndStageId(userId, stage.getId()))
                .thenReturn(Mono.just(new UserStage(1L, userId, stage.getId(), ContentState.ACTIVE)));
        when(scenarioRepository.findAllByStageId(stage.getId())).thenReturn(Flux.just(scenario));
        when(userScenarioRepository.findByUserIdAndScenarioId(userId, scenario.getId()))
                .thenReturn(Mono.just(new UserScenario(1L, userId, scenario.getId(), ContentState.ACTIVE)));

        Mono<AdventuresResponse> result = adventureService.getAdventures(userId);

        StepVerifier.create(result)
                .expectNextMatches(response ->
                        response.adventures().get(0).state().equals("ACTIVE") &&
                        response.adventures().get(0).stages().get(0).state().equals("ACTIVE") &&
                        response.adventures().get(0).stages().get(0).scenarios().get(0).state().equals("ACTIVE")
                )
                .verifyComplete();
    }
    
    @Test
    @DisplayName("유저_어드벤처_업데이트_성공")
    void updateUserAdventures_CallsServicesInOrder() {
        long userId = 1L;
        
        when(adventureInitService.activateFreeAdventures(userId)).thenReturn(Mono.empty());
        when(adventureProgressService.progressAllAdventures(userId)).thenReturn(Mono.empty());
        
        Mono<Void> result = adventureService.updateUserAdventures(userId);
        
        StepVerifier.create(result).verifyComplete();
        
        verify(adventureInitService).activateFreeAdventures(userId);
        verify(adventureProgressService).progressAllAdventures(userId);
    }
}