package com.wordonline.matching.adventure.service;

import com.wordonline.matching.adventure.domain.AccessType;
import com.wordonline.matching.adventure.domain.ContentState;
import com.wordonline.matching.adventure.domain.UserAdventure;
import com.wordonline.matching.adventure.repository.AdventureRepository;
import com.wordonline.matching.adventure.repository.ScenarioRepository;
import com.wordonline.matching.adventure.repository.StageRepository;
import com.wordonline.matching.adventure.repository.UserAdventureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AdventureInitService {

    private final AdventureRepository adventureRepository;
    private final StageRepository stageRepository;
    private final ScenarioRepository scenarioRepository;
    private final UserAdventureRepository userAdventureRepository;
    private final UserDataService userDataService;

    public Mono<Void> activateFreeAdventures(long userId) {
        return adventureRepository.findAll()
                .filter(adventure -> adventure.is(AccessType.FREE))
                .flatMap(freeAdventure ->
                        userAdventureRepository.findByUserIdAndAdventureId(userId, freeAdventure.getId())
                                .defaultIfEmpty(new UserAdventure(null, userId, freeAdventure.getId(), ContentState.INACTIVE))
                                .filter(userAdventure -> userAdventure.getState() == ContentState.INACTIVE)
                                .flatMap(inactiveUserAdventure ->
                                        userDataService.saveUserAdventure(userId, freeAdventure.getId(), ContentState.ACTIVE)
                                                .then(stageRepository.findAllByAdventureIdOrderByIdAsc(freeAdventure.getId()).next()
                                                        .flatMap(firstStage ->
                                                                userDataService.saveUserStage(userId, firstStage.getId(), ContentState.ACTIVE)
                                                                        .then(scenarioRepository.findAllByStageIdOrderByIdAsc(firstStage.getId()).next()
                                                                                .flatMap(firstScenario -> userDataService.saveUserScenario(userId, firstScenario.getId(), ContentState.ACTIVE).then())
                                                                        )
                                                        )
                                                )
                                )
                ).then();
    }
}