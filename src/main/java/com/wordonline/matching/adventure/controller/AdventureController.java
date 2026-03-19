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
    public Mono<AdventuresResponse> getAdventures(@AuthenticationPrincipal Jwt principalDetails) {
        Long memberId = principalDetails.getClaim("memberId");
        return adventureService.updateUserAdventures(memberId)
                        .then(adventureService.getAdventures(memberId));
    }

    @GetMapping(value = "/scenarios/{scenarioId}/play",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Object> playStage(
            @AuthenticationPrincipal Jwt principalDetails,
            @PathVariable Long scenarioId) {
        Long memberId = principalDetails.getClaim("memberId");
        return matchingService.requestPVE(memberId, scenarioId);
    }
}
