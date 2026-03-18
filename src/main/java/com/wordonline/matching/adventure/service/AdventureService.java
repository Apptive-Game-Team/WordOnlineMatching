package com.wordonline.matching.adventure.service;

import com.wordonline.matching.adventure.domain.Adventure;
import com.wordonline.matching.adventure.domain.ContentState;
import com.wordonline.matching.adventure.domain.Scenario;
import com.wordonline.matching.adventure.domain.Stage;
import com.wordonline.matching.adventure.domain.UserAdventure;
import com.wordonline.matching.adventure.domain.UserScenario;
import com.wordonline.matching.adventure.domain.UserStage;
import com.wordonline.matching.adventure.dto.AdventureDto;
import com.wordonline.matching.adventure.dto.AdventuresResponse;
import com.wordonline.matching.adventure.dto.ScenarioDto;
import com.wordonline.matching.adventure.dto.StageDto;
import com.wordonline.matching.adventure.repository.AdventureRepository;
import com.wordonline.matching.adventure.repository.ScenarioRepository;
import com.wordonline.matching.adventure.repository.StageRepository;
import com.wordonline.matching.adventure.repository.UserAdventureRepository;
import com.wordonline.matching.adventure.repository.UserScenarioRepository;
import com.wordonline.matching.adventure.repository.UserStageRepository;
import com.wordonline.matching.quest.service.QuestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AdventureService {

    private final AdventureRepository adventureRepository;
    private final StageRepository stageRepository;
    private final ScenarioRepository scenarioRepository;
    private final UserAdventureRepository userAdventureRepository;
    private final UserScenarioRepository userScenarioRepository;
    private final UserStageRepository userStageRepository;
    private final AdventureInitService adventureInitService;
    private final AdventureProgressService adventureProgressService;

    public Mono<AdventuresResponse> getAdventures(long userId) {
        return adventureRepository.findAll()
                .flatMap(adventure -> buildAdventureDto(userId, adventure))
                .collectList()
                .map(AdventuresResponse::new);
    }

    public Mono<Void> updateUserAdventures(long userId) {
        return adventureInitService.activateFreeAdventures(userId)
                .then(adventureProgressService.progressAllAdventures(userId));
    }

    private Mono<AdventureDto> buildAdventureDto(long userId, Adventure adventure) {
        return userAdventureRepository.findByUserIdAndAdventureId(userId, adventure.getId())
                .map(UserAdventure::getState)
                .defaultIfEmpty(ContentState.INACTIVE)
                .flatMap(state -> stageRepository.findAllByAdventureId(adventure.getId())
                        .flatMap(stage -> buildStageDto(userId, stage))
                        .collectList()
                        .map(stages -> new AdventureDto(adventure.getId(), state.name(), stages)));
    }

    private Mono<StageDto> buildStageDto(long userId, Stage stage) {
        return userStageRepository.findByUserIdAndStageId(userId, stage.getId())
                .map(UserStage::getState)
                .defaultIfEmpty(ContentState.INACTIVE)
                .flatMap(state -> scenarioRepository.findAllByStageId(stage.getId())
                        .flatMap(scenario -> buildScenarioDto(userId, scenario))
                        .collectList()
                        .map(scenarios -> new StageDto(stage.getId(), state.name(), scenarios)));
    }

    private Mono<ScenarioDto> buildScenarioDto(long userId, Scenario scenario) {
        return userScenarioRepository.findByUserIdAndScenarioId(userId, scenario.getId())
                .map(UserScenario::getState)
                .defaultIfEmpty(ContentState.INACTIVE)
                .map(state -> new ScenarioDto(scenario.getId(), state.name()));
    }
}