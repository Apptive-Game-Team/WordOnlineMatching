package com.wordonline.matching.matching.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.wordonline.matching.auth.service.UserId;
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
    public Flux<Object> queueMatching(@UserId Long userId) {
        log.info("[Queue] User queued for matching; userId: {}", userId.toString());
        Long memberId = userId;
        return matchingService.requestMatching(memberId);
    }

    @GetMapping(value = "/api/match/practice/me", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Object> matchPractice(@UserId Long userId) {
        Long memberId = userId;
        return matchingService.requestPractice(memberId);
    }

    @GetMapping("/api/match/queue/me/exist")
    public Mono<ResponseEntity<Void>> isMeInQueue(@UserId Long userId) {
        if (matchingService.isInQueue(userId)) {
            return Mono.just(ResponseEntity.ok().build());
        }
        return Mono.just(ResponseEntity.notFound().build());
    }

    @ResponseBody
    @DeleteMapping("/api/match/queue/me")
    public Mono<Void> removeFromQueue(@UserId Long userId) {
        return matchingService.removeFromQueue(userId);
    }

    @ResponseBody
    @GetMapping("/api/match/length")
    public Mono<QueueLengthResponseDto> getQueueLength() {
        return Mono.just(
                new QueueLengthResponseDto(matchingService.getQueueLength())
        );
    }
}
