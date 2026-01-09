package com.wordonline.matching.matching.service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;

@Slf4j
@Service
public class ServerEventService {

    private final Map<Long, Many<Object>> userSinks = new ConcurrentHashMap<>();

    public Flux<Object> subscribe(Long userId) {
        return subscribe(userId, null);
    }

    public Flux<Object> subscribe(Long userId, Consumer<Long> onFinal) {
        Many<Object> many = Sinks.many().unicast().onBackpressureBuffer();
        Flux<Object> heartbeatFlux = Flux.interval(Duration.ofSeconds(5))
                .map(tick -> "heartbeat");
        userSinks.put(userId, many);
        log.info("User sink created");
        return Flux.merge(userSinks.get(userId)
                .asFlux(), heartbeatFlux)
                .doFinally(signalType -> {
                    userSinks.remove(userId);
                    if (onFinal != null) onFinal.accept(userId);
                    
                    log.info("User {} sink removed automatically. Reason: {}", userId, signalType);
                });
    }

    public Mono<Void> unsubscribe(long userId) {
        Many<Object> many = userSinks.remove(userId);
        log.info("User {} sink removed", userId);
        if (many != null) {
            many.tryEmitComplete();
        }
        return Mono.just(0).then();
    }

    public Mono<Boolean> send(long userId, Object data) {
        log.info("[Practice] Sending user id: {}", userId);
        if (userId < 0) {
            return Mono.just(true);
        }
        if (userSinks.containsKey(userId)) {
            return Mono.just(userSinks.get(userId)
                    .tryEmitNext(data)
                    .isSuccess());
        }

        log.info("[Practice] user {}'s sinks not found", userId);
        return Mono.just(false);
    }
}
