package com.wordonline.matching.matching.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import com.wordonline.matching.deck.service.DeckService;
import com.wordonline.matching.auth.service.UserService;
import com.wordonline.matching.matching.dto.SessionDto;
import com.wordonline.matching.matching.dto.SimpleMessageDto;
import com.wordonline.matching.session.service.GameMatchService;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchingService {

    private final ServerEventService serverEventService;
    private final Queue<Long> matchingQueue = new ConcurrentLinkedQueue<>();
    private final static AtomicInteger sessionIdCounter = new AtomicInteger(1);

    private final DeckService deckService;
    private final UserService userService;
    private final GameMatchService gameMatchService;
    private final BotMemberMaker botMemberMaker;

    public int getQueueLength() {
        return matchingQueue.size();
    }

    public boolean isInQueue(long userId) {
        return matchingQueue.contains(userId);
    }

    public Flux<Object> requestMatching(long userId) {
        return enqueue(userId)
                .flatMapMany(isSuccess -> {
                    if (isSuccess) {
                        return Flux.<Object>just(new SimpleMessageDto("Successfully Enqueued"))
                                .mergeWith(serverEventService.subscribe(userId, finalUserId -> {
                                    matchingQueue.remove((Long) finalUserId);
                                    userService.markOnline(finalUserId)
                                            .doOnError(e -> log.error("Failed to mark online", e))
                                            .subscribe();
                                }));
                    } else {
                        return Flux.just(new SimpleMessageDto("Failed to enqueue user"));
                    }
                });
    }

    public Flux<Object> requestPractice(long userId) {
        SessionDto sessionDto = SessionDto.Practice("bot-" + sessionIdCounter.incrementAndGet(), userId, botMemberMaker.getRandomBotMemberId());
        return requestSession(userId, sessionDto);
    }

    public Flux<Object> requestPVE(long userId, long scenarioId) {
        SessionDto sessionDto = SessionDto.PVE("pve-" + sessionIdCounter.incrementAndGet(), userId, scenarioId);
        return requestSession(userId, sessionDto);
    }

    public Flux<Object> requestSession(long userId, SessionDto sessionDto) {
        Flux<Object> userFlux = serverEventService.subscribe(userId);
        matchingQueue.remove(userId);

        Mono.empty()
                .then(Mono.delay(Duration.ofSeconds(1))
                .then(matchBySessionDto(sessionDto)))
                .subscribe();

        return Flux.<Object>just(new SimpleMessageDto("Successfully Enqueued"))
                .mergeWith(userFlux);
    }

    private Mono<Boolean> enqueue(long userId) {
        if (isInQueue(userId)) {
            return Mono.just(true);
        }

        return userService.markMatching(userId)
                .then(deckService.hasSelectedDeck(userId))
                .flatMap(has -> {
                    if (!has) {
                        return Mono.error(new IllegalStateException("Deck has no selected"));
                    }
                    if (!isInQueue(userId))
                        matchingQueue.add(userId);
                    return Mono.just(true);
                }).onErrorResume(e -> {
                            log.warn("매칭 불가 상태: {} -> {}", userId, e.getMessage());
                            return userService.markOnline(userId)
                                    .thenReturn(false);
                        }
                );
    }

    public Mono<Void> removeFromQueue(long id) {
        matchingQueue.remove(id);
        return serverEventService.unsubscribe(id);
    }

    public Mono<Void> tryMatching() {
        int matchingNum = matchingQueue.size() / 2;
        return Flux.range(0, matchingNum)
                .flatMap(i -> tryMatchingOnSingleThread())
                .then();
    }

    private Mono<Boolean> tryMatchingOnSingleThread() {
        return tryMatchUsers()
                .subscribeOn(Schedulers.single());
    }

    private Mono<Boolean> tryMatchUsers() {
        if (matchingQueue.size() < 2) {
            return Mono.just(false);
        }

        long sessionId = sessionIdCounter.incrementAndGet();

        Long uid1 = matchingQueue.poll();
        Long uid2 = matchingQueue.poll();

        if (uid1 == null || uid2 == null) {
            return Mono.just(false);
        }

        return createSession(sessionId, uid1, uid2);
    }

    public Mono<Boolean> matchBySessionDto(SessionDto sessionDto) {
        return Mono.empty()
                .then(createSession(sessionDto))
                .map(isSuccess -> {
                    log.info("[Practice] Trying to create session {}", isSuccess);
                    return isSuccess;
                })
                .onErrorResume(e -> {
                    log.error("Failed to match practice", e);
                    return Mono.just(false);
                });
    }

    private Mono<Boolean> createSession(long sessionId, long uid1, long uid2) {
        return createSession("session-" + sessionId, uid1, uid2);
    }

    private Mono<Boolean> createSession(String sessionId, long uid1, long uid2) {
        SessionDto sessionDto = SessionDto.from(sessionId, uid1, uid2);
        return createSession(sessionDto);
    }

    private Mono<Boolean> createSession(SessionDto sessionDto) {
        return gameMatchService.createSession(sessionDto)
                .flatMap(matchedInfoDto -> Mono.zip(
                                userService.markPlaying(sessionDto.uid1()),
                                userService.markPlaying(sessionDto.uid2()))
                        .thenReturn(matchedInfoDto))
                .flatMap(matchedInfoDto -> {
                    log.info("[Session] Session created: {}, uid1: {}, uid2: {}", sessionDto.sessionId(), sessionDto.uid1(), sessionDto.uid2());
                    return Mono.zip(
                                    serverEventService.send(sessionDto.uid1(), matchedInfoDto),
                                    serverEventService.send(sessionDto.uid2(), matchedInfoDto))
                            .thenReturn(true);
                })
                .onErrorResume(e -> {
                            log.error("Failed to create session", e);
                            return Mono.zip(
                                    userService.markOnline(sessionDto.uid1()),
                                    userService.markOnline(sessionDto.uid2())
                            ).thenReturn(false);
                        }
                );
    }

    public String getHealthLog() {
        return "Matching Queue: " + matchingQueue;
    }
}
