package com.wordonline.matching.adventure.service;

import com.wordonline.matching.adventure.domain.ContentState;
import com.wordonline.matching.adventure.domain.UserAdventure;
import com.wordonline.matching.adventure.domain.UserScenario;
import com.wordonline.matching.adventure.domain.UserStage;
import com.wordonline.matching.adventure.repository.UserAdventureRepository;
import com.wordonline.matching.adventure.repository.UserScenarioRepository;
import com.wordonline.matching.adventure.repository.UserStageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class UserDataService {

    private final UserAdventureRepository userAdventureRepository;
    private final UserStageRepository userStageRepository;
    private final UserScenarioRepository userScenarioRepository;

    public Mono<UserScenario> saveUserScenario(long userId, long scenarioId, ContentState state) {
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

    public Mono<Void> saveUserStage(long userId, long stageId, ContentState state) {
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

    public Mono<Void> saveUserAdventure(long userId, long adventureId, ContentState state) {
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
