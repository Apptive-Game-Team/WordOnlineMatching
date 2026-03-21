package com.wordonline.matching.adventure.controller;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wordonline.matching.adventure.dto.AdventuresResponse;
import com.wordonline.matching.adventure.service.AdventureService;
import com.wordonline.matching.auth.service.UserId;
import com.wordonline.matching.matching.service.MatchingService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AdventureController {

    private final AdventureService adventureService;
    private final MatchingService matchingService;

    @GetMapping("/adventures")
    public Mono<AdventuresResponse> getAdventures(@UserId Long userId) {
        return adventureService.updateUserAdventures(userId)
                        .then(adventureService.getAdventures(userId));
    }

    @GetMapping(value = "/scenarios/{scenarioId}/play",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Object> playStage(
            @UserId Long userId,
            @PathVariable Long scenarioId) {
        return matchingService.requestPVE(userId, scenarioId);
    }
}
