package com.wordonline.matching.adventure.service;

import com.wordonline.matching.adventure.repository.UserScenarioRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdventureInitService {

    private final UserScenarioRepository userScenarioRepository;

    public Mono<Void> activateFreeAdventures(long userId) {
        log.debug("Start activating free adventures for user: {}", userId);
        return userScenarioRepository.updateStateActiveFreeAdventure(userId);
    }
}