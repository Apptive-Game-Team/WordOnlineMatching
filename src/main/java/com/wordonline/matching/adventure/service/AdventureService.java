package com.wordonline.matching.adventure.service;

import org.springframework.stereotype.Service;

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
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AdventureService {

    private final AdventureRepository adventureRepository;
    private final StageRepository stageRepository;
    private final ScenarioRepository scenarioRepository;
    private final UserAdventureRepository userAdventureRepository;
    private final UserStageRepository userStageRepository;
    private final UserScenarioRepository userScenarioRepository;
    private final QuestService questService;

    public Mono<AdventuresResponse> getAdventures(long userId) {
        return adventureRepository.findAll()
                .flatMap(adventure -> buildAdventureDto(userId, adventure))
                .collectList()
                .map(AdventuresResponse::new);
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

    public Mono<Void> clearScenario(long userId, long stageId, long scenarioId) {
        return saveUserScenarioFinished(userId, scenarioId)
                .then(checkAndUpdateStageState(userId, stageId))
                .then(questService.checkQuests(userId));
    }

    public Mono<Void> activateStage(long userId, long adventureId, long stageId) {
        return stageRepository.findById(stageId)
                .filter(stage -> adventureId == stage.getAdventureId())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Stage not found in adventure")))
                .flatMap(stage -> userStageRepository.findByUserIdAndStageId(userId, stageId)
                        .switchIfEmpty(Mono.defer(() ->
                                userStageRepository.save(new UserStage(null, userId, stageId, ContentState.ACTIVE))))
                        .flatMap(existing -> {
                            if (existing.getState() == ContentState.INACTIVE) {
                                return userStageRepository.save(
                                        new UserStage(existing.getId(), userId, stageId, ContentState.ACTIVE));
                            }
                            return Mono.just(existing);
                        }))
                .then();
    }

    private Mono<Void> saveUserScenarioFinished(long userId, long scenarioId) {
        return saveUserScenario(userId, scenarioId, ContentState.FINISHED)
                .then();
    }

    private Mono<UserScenario> saveUserScenario(long userId, long scenarioId, ContentState state) {
        return userScenarioRepository.findByUserIdAndScenarioId(userId, scenarioId)
                .switchIfEmpty(Mono.defer(() ->
                        userScenarioRepository.save(new UserScenario(null, userId, scenarioId, state))))
                .flatMap(existing -> {
                    if (existing.getState() != state) {
                        return userScenarioRepository.save(
                                new UserScenario(existing.getId(), userId, scenarioId, state));
                    }
                    return Mono.just(existing);
                });
    }

    private Mono<Void> checkAndUpdateStageState(long userId, long stageId) {
        return stageRepository.findById(stageId)
                .flatMap(stage ->
                        scenarioRepository.findAllByStageId(stageId)
                                .flatMap(scenario ->
                                        userScenarioRepository.findByUserIdAndScenarioId(userId, scenario.getId())
                                                .defaultIfEmpty(new UserScenario(null, userId, scenario.getId(), ContentState.INACTIVE)))
                                .collectList()
                                .flatMap(userScenarios -> {
                                    boolean allFinished = userScenarios.stream()
                                            .allMatch(us -> us.getState() == ContentState.FINISHED);
                                    ContentState newState = allFinished ? ContentState.FINISHED : ContentState.ACTIVE;
                                    return saveUserStage(userId, stageId, newState)
                                            .then(checkAndUpdateAdventureState(userId, stage.getAdventureId()));
                                })
                );
    }

    private Mono<Void> saveUserStage(long userId, long stageId, ContentState state) {
        return userStageRepository.findByUserIdAndStageId(userId, stageId)
                .switchIfEmpty(Mono.defer(() ->
                        userStageRepository.save(new UserStage(null, userId, stageId, state))))
                .flatMap(existing -> {
                    if (existing.getState() != state) {
                        return userStageRepository.save(
                                new UserStage(existing.getId(), userId, stageId, state));
                    }
                    return Mono.just(existing);
                })
                .then();
    }

    private Mono<Void> checkAndUpdateAdventureState(long userId, long adventureId) {
        return stageRepository.findAllByAdventureId(adventureId)
                .flatMap(stage ->
                        userStageRepository.findByUserIdAndStageId(userId, stage.getId())
                                .defaultIfEmpty(new UserStage(null, userId, stage.getId(), ContentState.INACTIVE)))
                .collectList()
                .flatMap(userStages -> {
                    boolean allFinished = userStages.stream()
                            .allMatch(us -> us.getState() == ContentState.FINISHED);
                    boolean anyActive = userStages.stream()
                            .anyMatch(us -> us.getState() == ContentState.ACTIVE
                                    || us.getState() == ContentState.FINISHED);
                    ContentState newState = allFinished ? ContentState.FINISHED
                            : (anyActive ? ContentState.ACTIVE : ContentState.INACTIVE);
                    return saveUserAdventure(userId, adventureId, newState);
                });
    }

    private Mono<Void> saveUserAdventure(long userId, long adventureId, ContentState state) {
        return userAdventureRepository.findByUserIdAndAdventureId(userId, adventureId)
                .switchIfEmpty(Mono.defer(() ->
                        userAdventureRepository.save(new UserAdventure(null, userId, adventureId, state))))
                .flatMap(existing -> {
                    if (existing.getState() != state) {
                        return userAdventureRepository.save(
                                new UserAdventure(existing.getId(), userId, adventureId, state));
                    }
                    return Mono.just(existing);
                })
                .then();
    }
}
