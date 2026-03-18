package com.wordonline.matching.matching.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.wordonline.matching.matching.dto.QueueLengthResponseDto;
import com.wordonline.matching.matching.service.MatchingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequiredArgsConstructor
public class MatchingController {

    private final MatchingService matchingService;

    @GetMapping(value = "/api/match/queue/me", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Object> queueMatching(@AuthenticationPrincipal Jwt principalDetails) {
        log.info("[Queue] User queued for matching; userId: {}", principalDetails.getClaim("memberId").toString());
        Long memberId = principalDetails.getClaim("memberId");
        return matchingService.requestMatching(memberId);
    }

    @GetMapping(value = "/api/match/practice/me", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Object> matchPractice(@AuthenticationPrincipal Jwt principalDetails) {
        Long memberId = principalDetails.getClaim("memberId");
        return matchingService.requestPractice(memberId);
    }

    @GetMapping("/api/match/queue/me/exist")
    public Mono<ResponseEntity<Void>> isMeInQueue(@AuthenticationPrincipal Jwt principalDetails) {
        if (matchingService.isInQueue(principalDetails.getClaim("memberId"))) {
            return Mono.just(ResponseEntity.ok().build());
        }
        return Mono.just(ResponseEntity.notFound().build());
    }

    @ResponseBody
    @DeleteMapping("/api/match/queue/me")
    public Mono<Void> removeFromQueue(@AuthenticationPrincipal Jwt principalDetails) {
        return matchingService.removeFromQueue(principalDetails.getClaim("memberId"));
    }

    @ResponseBody
    @GetMapping("/api/match/length")
    public Mono<QueueLengthResponseDto> getQueueLength() {
        return Mono.just(
                new QueueLengthResponseDto(matchingService.getQueueLength())
        );
    }
}
