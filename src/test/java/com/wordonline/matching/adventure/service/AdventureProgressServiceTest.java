package com.wordonline.matching.adventure.service;

import com.wordonline.matching.adventure.domain.ContentState;
import com.wordonline.matching.adventure.domain.Scenario;
import com.wordonline.matching.adventure.domain.Stage;
import com.wordonline.matching.adventure.domain.UserAdventure;
import com.wordonline.matching.adventure.domain.UserScenario;
import com.wordonline.matching.adventure.repository.ScenarioRepository;
import com.wordonline.matching.adventure.repository.StageRepository;
import com.wordonline.matching.adventure.repository.UserAdventureRepository;
import com.wordonline.matching.adventure.repository.UserScenarioRepository;
import com.wordonline.matching.adventure.repository.UserStageRepository;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdventureProgressServiceTest {

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
    @Mock
    private UserDataService userDataService;

    @InjectMocks
    private AdventureProgressService adventureProgressService;

    private Stage stage1;
    private Scenario scenario1_1, scenario1_2;
    private UserScenario userScenario1_1_finished, userScenario1_2_inactive;

    @BeforeEach
    void setUp() {
        stage1 = new Stage(1L, 1L);
        scenario1_1 = new Scenario(1L, 1L);
        scenario1_2 = new Scenario(2L, 1L);

        userScenario1_1_finished = new UserScenario(1L, 1L, 1L, ContentState.FINISHED);
        userScenario1_2_inactive = new UserScenario(2L, 1L, 2L, ContentState.INACTIVE);
    }

    @Test
    @DisplayName("checkAndActive_다음_시나리오_활성화_성공")
    void checkAndActive_activatesNextScenario() {
        long userId = 1L;
        long adventureId = 1L;

        // Mock a flat list of scenarios for the adventure
        when(stageRepository.findAllByAdventureIdOrderByIdAsc(adventureId)).thenReturn(Flux.just(stage1));
        when(scenarioRepository.findAllByStageIdOrderByIdAsc(stage1.getId())).thenReturn(Flux.just(scenario1_1, scenario1_2));

        // Mock user progress for each scenario
        when(userScenarioRepository.findByUserIdAndScenarioId(userId, scenario1_1.getId())).thenReturn(Mono.just(userScenario1_1_finished));
        when(userScenarioRepository.findByUserIdAndScenarioId(userId, scenario1_2.getId())).thenReturn(Mono.just(userScenario1_2_inactive));
        
        // Mock the stage's current state (not finished)
        when(userStageRepository.findByUserIdAndStageId(anyLong(), anyLong())).thenReturn(Mono.empty());

        // Mock the save/activation calls
        when(userDataService.saveUserScenario(userId, scenario1_2.getId(), ContentState.ACTIVE)).thenReturn(Mono.just(new UserScenario()));
        when(userDataService.saveUserStage(userId, stage1.getId(), ContentState.ACTIVE)).thenReturn(Mono.empty());

        Mono<Void> result = adventureProgressService.checkAndActive(userId, adventureId);

        StepVerifier.create(result)
                .verifyComplete();

        // Verify that the next scenario and its stage were activated
        verify(userDataService).saveUserScenario(userId, scenario1_2.getId(), ContentState.ACTIVE);
        verify(userDataService).saveUserStage(userId, stage1.getId(), ContentState.ACTIVE);
    }
    
    @Test
    @DisplayName("progressAllAdventures_전체_진행_및_상태_업데이트")
    void progressAllAdventures_updatesAll() {
        long userId = 1L;
        long adventureId = 1L;
        
        UserAdventure userAdventure = new UserAdventure(1L, userId, adventureId, ContentState.ACTIVE);
        
        when(userAdventureRepository.findAllByUserId(userId)).thenReturn(Flux.just(userAdventure));
        when(userStageRepository.findAllByUserId(userId)).thenReturn(Flux.empty()); // Assume no stages to update for simplicity
        
        // Mock the inner call to checkAndActive to do nothing
        when(stageRepository.findAllByAdventureIdOrderByIdAsc(adventureId)).thenReturn(Flux.empty());
        
        Mono<Void> result = adventureProgressService.progressAllAdventures(userId);
        
        StepVerifier.create(result)
            .verifyComplete();
            
        // Verify that checkAndActive was called for the user's adventure
        verify(stageRepository).findAllByAdventureIdOrderByIdAsc(adventureId);
    }
}
