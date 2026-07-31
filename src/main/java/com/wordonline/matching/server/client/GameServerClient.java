package com.wordonline.matching.server.client;

import java.time.Duration;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.wordonline.matching.server.dto.RoomListDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameServerClient {

    private final WebClient.Builder webClientBuilder;

    /**
     * Errors propagate on purpose. A failed lookup must stay distinguishable from "this server
     * has no rooms": callers that reconcile session state would otherwise read a transport failure
     * as every game having ended.
     */
    public Mono<RoomListDto> getGameSessions(String serverUrl) {
        log.info("Fetching game sessions from server: {}", serverUrl);

        WebClient webClient = webClientBuilder.baseUrl(serverUrl).build();

        return webClient.get()
                .uri("/api/server/game-sessions")
                .retrieve()
                .bodyToMono(RoomListDto.class)
                .timeout(Duration.ofSeconds(3))
                .doOnError(error -> log.error("Failed to fetch game sessions from server: {}", serverUrl, error));
    }

    public Mono<Boolean> healthcheck(String serverUrl) {
        WebClient webClient = webClientBuilder.baseUrl(serverUrl).build();

        return webClient.get()
                .uri("/healthcheck")
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(2))
                .map(responseEntity -> responseEntity.getStatusCode().is2xxSuccessful())
                .onErrorReturn(false);
    }
}
