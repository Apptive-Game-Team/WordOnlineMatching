package com.wordonline.matching.adventure.service;

import com.wordonline.matching.adventure.domain.ContentState;
import com.wordonline.matching.adventure.domain.Scenario;
import com.wordonline.matching.adventure.domain.UserScenario;
import com.wordonline.matching.adventure.domain.UserStage;
import com.wordonline.matching.adventure.repository.ScenarioRepository;
import com.wordonline.matching.adventure.repository.StageRepository;
import com.wordonline.matching.adventure.repository.UserAdventureRepository;
import com.wordonline.matching.adventure.repository.UserScenarioRepository;
import com.wordonline.matching.adventure.repository.UserStageRepository;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AdventureProgressService {

    private final StageRepository stageRepository;
    private final ScenarioRepository scenarioRepository;
    private final UserAdventureRepository userAdventureRepository;
    private final UserStageRepository userStageRepository;
    private final UserScenarioRepository userScenarioRepository;
    private final UserDataService userDataService;

    @Getter
    @AllArgsConstructor
    private static class UserScenarioState {
        private Scenario scenario;
        private ContentState state;
    }

    public Mono<Void> progressAllAdventures(long userId) {
        Mono<Void> updateAndActiveAll = userAdventureRepository.findAllByUserId(userId)
                .flatMap(userAdventure -> checkAndActive(userId, userAdventure.getAdventureId()))
                .then();

        Mono<Void> checkAndUpdateAll = userStageRepository.findAllByUserId(userId)
                .map(UserStage::getStageId)
                .flatMap(stageId -> checkAndUpdateStageState(userId, stageId))
                .then();

        return updateAndActiveAll.then(checkAndUpdateAll);
    }


    public Mono<Void> checkAndActive(long userId, long adventureId) {
        Flux<Scenario> allScenarios = stageRepository.findAllByAdventureIdOrderByIdAsc(adventureId)
                .flatMap(stage -> scenarioRepository.findAllByStageIdOrderByIdAsc(stage.getId()));

        return allScenarios.flatMap(scenario ->
                        userScenarioRepository.findByUserIdAndScenarioId(userId, scenario.getId())
                                .map(userScenario -> new UserScenarioState(scenario, userScenario.getState()))
                                .defaultIfEmpty(new UserScenarioState(scenario, ContentState.INACTIVE))
                )
                .collectList()
                .flatMap(userScenarioStates -> {
                    int lastFinishedIndex = -1;
                    for (int i = 0; i < userScenarioStates.size(); i++) {
                        if (userScenarioStates.get(i).getState() == ContentState.FINISHED) {
                            lastFinishedIndex = i;
                        }
                    }

                    if (lastFinishedIndex + 1 < userScenarioStates.size()) {
                        UserScenarioState toActivate = userScenarioStates.get(lastFinishedIndex + 1);

                        if (toActivate.getState() == ContentState.INACTIVE) {
                            Mono<Void> activateScenarioMono = userDataService.saveUserScenario(userId, toActivate.getScenario().getId(), ContentState.ACTIVE).then();

                            long stageIdToEnsureActive = toActivate.getScenario().getStageId();
                            Mono<Void> activateStageMono = userStageRepository.findByUserIdAndStageId(userId, stageIdToEnsureActive)
                                    .defaultIfEmpty(new UserStage(null, userId, stageIdToEnsureActive, ContentState.INACTIVE))
                                    .flatMap(userStage -> {
                                        if (userStage.getState() == ContentState.INACTIVE) {
                                            return userDataService.saveUserStage(userId, stageIdToEnsureActive, ContentState.ACTIVE);
                                        }
                                        return Mono.empty();
                                    }).then();

                            return activateScenarioMono.then(activateStageMono);
                        }
                    }

                    return Mono.empty();
                }).then();
    }

    public Mono<Void> checkAndUpdateStageState(long userId, long stageId) {
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
                                    return userDataService.saveUserStage(userId, stageId, newState)
                                            .then(checkAndUpdateAdventureState(userId, stage.getAdventureId()));
                                })
                );
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
                    return userDataService.saveUserAdventure(userId, adventureId, newState);
                });
    }
}
