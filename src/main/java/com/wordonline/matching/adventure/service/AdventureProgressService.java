package com.wordonline.matching.adventure.service;

import com.wordonline.matching.adventure.repository.UserScenarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdventureProgressService {

    private final UserScenarioRepository userScenarioRepository;

    public Mono<Void> progressAllAdventures(long userId) {
        log.debug("Start progressing all adventures for user: {}", userId);
        return userScenarioRepository.updateStateActiveWhenBeforeScenarioIsFinished(userId);
    }

}
