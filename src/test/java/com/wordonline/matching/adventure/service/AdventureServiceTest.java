package com.wordonline.matching.adventure.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AdventureServiceTest {

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
    @Mock
    private QuestService questService;

    @InjectMocks
    private AdventureService adventureService;

    private Adventure adventure;
    private Stage stage;
    private Scenario scenario;

    @BeforeEach
    void setUp() {
        adventure = new Adventure(1L);
        stage = new Stage(1L, 1L);
        scenario = new Scenario(1L, 1L);
    }

    @Test
    @DisplayName("모험_목록_조회_성공_유저_진행상황_없음")
    void getAdventures_NoUserProgress_ReturnsInactiveStates() {
        long userId = 1L;

        when(adventureRepository.findAll()).thenReturn(Flux.just(adventure));
        when(userAdventureRepository.findByUserIdAndAdventureId(userId, adventure.getId()))
                .thenReturn(Mono.empty());
        when(stageRepository.findAllByAdventureId(adventure.getId())).thenReturn(Flux.just(stage));
        when(userStageRepository.findByUserIdAndStageId(userId, stage.getId())).thenReturn(Mono.empty());
        when(scenarioRepository.findAllByStageId(stage.getId())).thenReturn(Flux.just(scenario));
        when(userScenarioRepository.findByUserIdAndScenarioId(userId, scenario.getId())).thenReturn(Mono.empty());

        Mono<AdventuresResponse> result = adventureService.getAdventures(userId);

        StepVerifier.create(result)
                .expectNextMatches(response -> {
                    var adv = response.adventures().get(0);
                    var stg = adv.stages().get(0);
                    var scn = stg.scenarios().get(0);
                    return adv.state().equals("Inactive")
                            && stg.state().equals("Inactive")
                            && scn.state().equals("Inactive");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("모험_목록_조회_성공_유저_진행상황_있음")
    void getAdventures_WithUserProgress_ReturnsCorrectStates() {
        long userId = 1L;

        when(adventureRepository.findAll()).thenReturn(Flux.just(adventure));
        when(userAdventureRepository.findByUserIdAndAdventureId(userId, adventure.getId()))
                .thenReturn(Mono.just(new UserAdventure(1L, userId, adventure.getId(), ContentState.Active)));
        when(stageRepository.findAllByAdventureId(adventure.getId())).thenReturn(Flux.just(stage));
        when(userStageRepository.findByUserIdAndStageId(userId, stage.getId()))
                .thenReturn(Mono.just(new UserStage(1L, userId, stage.getId(), ContentState.Finished)));
        when(scenarioRepository.findAllByStageId(stage.getId())).thenReturn(Flux.just(scenario));
        when(userScenarioRepository.findByUserIdAndScenarioId(userId, scenario.getId()))
                .thenReturn(Mono.just(new UserScenario(1L, userId, scenario.getId(), ContentState.Finished)));

        Mono<AdventuresResponse> result = adventureService.getAdventures(userId);

        StepVerifier.create(result)
                .expectNextMatches(response -> {
                    var adv = response.adventures().get(0);
                    var stg = adv.stages().get(0);
                    var scn = stg.scenarios().get(0);
                    return adv.state().equals("Active")
                            && stg.state().equals("Finished")
                            && scn.state().equals("Finished");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("시나리오_클리어_성공_모든_시나리오_완료_시_스테이지_완료")
    void clearScenario_AllScenariosCleared_StageFinished() {
        long userId = 1L;
        long stageId = 1L;
        long scenarioId = 1L;

        UserScenario finishedScenario = new UserScenario(1L, userId, scenarioId, ContentState.Finished);
        UserStage finishedStage = new UserStage(1L, userId, stage.getId(), ContentState.Finished);
        UserAdventure finishedAdventure = new UserAdventure(1L, userId, adventure.getId(), ContentState.Finished);

        when(userScenarioRepository.findByUserIdAndScenarioId(userId, scenarioId))
                .thenReturn(Mono.just(finishedScenario));
        when(stageRepository.findById(stageId)).thenReturn(Mono.just(stage));
        when(scenarioRepository.findAllByStageId(stage.getId())).thenReturn(Flux.just(scenario));
        when(userStageRepository.findByUserIdAndStageId(userId, stageId))
                .thenReturn(Mono.just(finishedStage));
        when(stageRepository.findAllByAdventureId(adventure.getId())).thenReturn(Flux.just(stage));
        when(userAdventureRepository.findByUserIdAndAdventureId(userId, adventure.getId()))
                .thenReturn(Mono.empty());
        when(userAdventureRepository.save(any(UserAdventure.class)))
                .thenReturn(Mono.just(finishedAdventure));
        when(questService.checkQuests(userId)).thenReturn(Mono.empty());

        Mono<Void> result = adventureService.clearScenario(userId, stageId, scenarioId);

        StepVerifier.create(result)
                .verifyComplete();

        verify(questService).checkQuests(userId);
    }

    @Test
    @DisplayName("스테이지_활성화_성공_비활성_상태에서_활성_상태로_변경")
    void activateStage_FromInactive_ChangesToActive() {
        long userId = 1L;
        long adventureId = 1L;
        long stageId = 1L;

        when(stageRepository.findById(stageId)).thenReturn(Mono.just(stage));
        when(userStageRepository.findByUserIdAndStageId(userId, stageId))
                .thenReturn(Mono.just(new UserStage(1L, userId, stageId, ContentState.Inactive)));
        when(userStageRepository.save(any(UserStage.class)))
                .thenReturn(Mono.just(new UserStage(1L, userId, stageId, ContentState.Active)));

        Mono<Void> result = adventureService.activateStage(userId, adventureId, stageId);

        StepVerifier.create(result)
                .verifyComplete();

        verify(userStageRepository).save(any(UserStage.class));
    }

    @Test
    @DisplayName("스테이지_활성화_이미_활성_상태면_변경_없음")
    void activateStage_AlreadyActive_NoChange() {
        long userId = 1L;
        long adventureId = 1L;
        long stageId = 1L;

        when(stageRepository.findById(stageId)).thenReturn(Mono.just(stage));
        when(userStageRepository.findByUserIdAndStageId(userId, stageId))
                .thenReturn(Mono.just(new UserStage(1L, userId, stageId, ContentState.Active)));

        Mono<Void> result = adventureService.activateStage(userId, adventureId, stageId);

        StepVerifier.create(result)
                .verifyComplete();
    }
}
