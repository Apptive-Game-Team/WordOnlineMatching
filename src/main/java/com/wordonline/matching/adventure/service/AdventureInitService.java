package com.wordonline.matching.adventure.service;

import com.wordonline.matching.adventure.domain.AccessType;
import com.wordonline.matching.adventure.domain.ContentState;
import com.wordonline.matching.adventure.domain.UserAdventure;
import com.wordonline.matching.adventure.repository.AdventureRepository;
import com.wordonline.matching.adventure.repository.ScenarioRepository;
import com.wordonline.matching.adventure.repository.StageRepository;
import com.wordonline.matching.adventure.repository.UserAdventureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdventureInitService {

    private final AdventureRepository adventureRepository;
    private final StageRepository stageRepository;
    private final ScenarioRepository scenarioRepository;
    private final UserAdventureRepository userAdventureRepository;
    private final UserDataService userDataService;

    public Mono<Void> activateFreeAdventures(long userId) {
        log.debug("Start activating free adventures for user: {}", userId);
        return adventureRepository.findAll()
                .filter(adventure -> adventure.is(AccessType.FREE))
                .flatMap(freeAdventure -> {
                            log.debug("Processing free adventure: {} for user: {}", freeAdventure.getId(), userId);
                            return userAdventureRepository.findByUserIdAndAdventureId(userId, freeAdventure.getId())
                                    .defaultIfEmpty(new UserAdventure(null, userId, freeAdventure.getId(), ContentState.INACTIVE))
                                    .filter(userAdventure -> userAdventure.getState() == ContentState.INACTIVE)
                                    .flatMap(inactiveUserAdventure -> saveAdventure(userId, inactiveUserAdventure));
                        }
                ).then()
                .doOnSuccess(aVoid -> log.debug("Finished activating free adventures for user: {}", userId));
    }

    private Mono<Void> saveAdventure(long userId, UserAdventure inactiveUserAdventure) {
        inactiveUserAdventure.setState(ContentState.ACTIVE);
        return userAdventureRepository.save(inactiveUserAdventure)
                .then(stageRepository.findAllByAdventureIdOrderByIdAsc(inactiveUserAdventure.getId()).next()
                        .flatMap(firstStage -> {
                                    log.debug("Saving first stage: {} for user: {}", firstStage.getId(), userId);
                                    return userDataService.saveUserStage(userId, firstStage.getId(), ContentState.ACTIVE)
                                            .then(scenarioRepository.findAllByStageIdOrderByIdAsc(firstStage.getId()).next()
                                                    .flatMap(firstScenario -> {
                                                        log.debug("Saving first scenario: {} for user: {}", firstScenario.getId(), userId);
                                                        return userDataService.saveUserScenario(userId, firstScenario.getId(), ContentState.ACTIVE).then();
                                                    })
                                            );
                                }
                        )
                );
    }
}