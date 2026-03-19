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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
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
        log.debug("Start progressing all adventures for user: {}", userId);

        Mono<Void> updateAndActiveAll = userAdventureRepository.findAllByUserId(userId)
                .flatMap(userAdventure -> {
                    return checkAndActive(userId, userAdventure.getAdventureId());
                })
                .then();

        Mono<Void> checkAndUpdateAll = userStageRepository.findAllByUserId(userId)
                .map(UserStage::getStageId)
                .flatMap(stageId -> {
                    log.debug("Checking and updating stage state for stage: {} for user: {}", stageId, userId);
                    return checkAndUpdateStageState(userId, stageId);
                })
                .then();

        return updateAndActiveAll.then(checkAndUpdateAll)
                .doOnSuccess(aVoid -> log.debug("Finished progressing all adventures for user: {}", userId));
    }


    public Mono<Void> checkAndActive(long userId, long adventureId) {
        log.debug("Checking and activating adventure: {} for user: {}", adventureId, userId);
        Flux<Scenario> allScenarios = stageRepository.findAllByAdventureIdOrderByIdAsc(adventureId)
                .flatMap(stage -> scenarioRepository.findAllByStageIdOrderByIdAsc(stage.getId()));

        return allScenarios.flatMap(scenario ->
                        userScenarioRepository.findByUserIdAndScenarioId(userId, scenario.getId())
                                .map(userScenario -> new UserScenarioState(scenario, userScenario.getState()))
                                .defaultIfEmpty(new UserScenarioState(scenario, ContentState.INACTIVE))
                )
                .collectList()
                .flatMap(userScenarioStates -> {
                    log.debug("Collected {} scenario states for user: {} in adventure: {}", userScenarioStates.size(), userId, adventureId);

                    int firstNonFinishedIndex = -1;
                    for (int i = 0; i < userScenarioStates.size(); i++) {
                        if (userScenarioStates.get(i).getState() != ContentState.FINISHED) {
                            firstNonFinishedIndex = i;
                            break;
                        }
                    }

                    // 모든 시나리오가 끝났거나, 진행할 시나리오가 없는 경우
                    if (firstNonFinishedIndex == -1) {
                        log.debug("All scenarios finished or no scenarios to process for user: {} in adventure: {}", userId, adventureId);
                        return Mono.empty();
                    }

                    // 첫 번째로 FINISHED가 아닌 시나리오를 가져옴
                    UserScenarioState toActivate = userScenarioStates.get(firstNonFinishedIndex);
                    log.debug("First non-finished scenario is at index {} with state: {} for user: {} in adventure: {}",
                            firstNonFinishedIndex, toActivate.getState(), userId, adventureId);

                    // 해당 시나리오가 INACTIVE이고, 이전 시나리오가 존재하며 FINISHED 상태일 때만 활성화
                    // firstNonFinishedIndex > 0 라는 것은 이전에 FINISHED 시나리오가 하나 이상 있다는 의미
                    if (toActivate.getState() == ContentState.INACTIVE && firstNonFinishedIndex > 0) {
                        log.debug("Activating scenario: {} for user: {}", toActivate.getScenario().getId(), userId);
                        Mono<Void> activateScenarioMono = userDataService.saveUserScenario(userId, toActivate.getScenario().getId(), ContentState.ACTIVE).then();

                        long stageIdToEnsureActive = toActivate.getScenario().getStageId();
                        log.debug("Ensuring stage: {} is active for user: {}", stageIdToEnsureActive, userId);
                        Mono<Void> activateStageMono = userStageRepository.findByUserIdAndStageId(userId, stageIdToEnsureActive)
                                .defaultIfEmpty(new UserStage(null, userId, stageIdToEnsureActive, ContentState.INACTIVE))
                                .flatMap(userStage -> {
                                    if (userStage.getState() == ContentState.INACTIVE) {
                                        log.debug("Activating stage: {} for user: {}", stageIdToEnsureActive, userId);
                                        return userDataService.saveUserStage(userId, stageIdToEnsureActive, ContentState.ACTIVE);
                                    }
                                    return Mono.empty();
                                }).then();

                        return activateScenarioMono.then(activateStageMono);
                    }

                    return Mono.empty();
                }).then();
    }

    public Mono<Void> checkAndUpdateStageState(long userId, long stageId) {
        log.debug("Checking and updating state for stage: {} for user: {}", stageId, userId);
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
                                    log.debug("Updating stage: {} for user: {} to state: {}", stageId, userId, newState);
                                    return userDataService.saveUserStage(userId, stageId, newState)
                                            .then(checkAndUpdateAdventureState(userId, stage.getAdventureId()));
                                })
                );
    }

    private Mono<Void> checkAndUpdateAdventureState(long userId, long adventureId) {
        log.debug("Checking and updating state for adventure: {} for user: {}", adventureId, userId);
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
                    log.debug("Updating adventure: {} for user: {} to state: {}", adventureId, userId, newState);
                    return userDataService.saveUserAdventure(userId, adventureId, newState);
                });
    }
}
