package com.wordonline.matching.server.client;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.wordonline.matching.server.dto.RoomListDto;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class GameServerClient {

    private final WebClient.Builder webClientBuilder;
    private final Duration healthcheckTimeout;

    public GameServerClient(
            WebClient.Builder webClientBuilder,
            @Value("${gameserver.healthcheck-timeout-ms:3000}") long healthcheckTimeoutMs
    ) {
        this.webClientBuilder = webClientBuilder;
        this.healthcheckTimeout = Duration.ofMillis(healthcheckTimeoutMs);
    }

    public Mono<RoomListDto> getGameSessions(String serverUrl) {
        log.info("Fetching game sessions from server: {}", serverUrl);

        WebClient webClient = webClientBuilder.baseUrl(serverUrl).build();

        return webClient.get()
                .uri("/api/server/game-sessions")
                .retrieve()
                .bodyToMono(RoomListDto.class)
                .onErrorResume(error -> {
                    log.error("Failed to fetch game sessions from server: {}", serverUrl, error);
                    return Mono.just(new RoomListDto(java.util.List.of()));
                });
    }

    /**
     * Probes the game server health endpoint.
     * <p>
     * {@code timeout} must stay <b>above</b> {@code onErrorReturn} so the emitted
     * {@link java.util.concurrent.TimeoutException} is absorbed into {@code false}
     * instead of escaping to the caller and terminating the surrounding health check
     * pipeline.
     *
     * @return {@code true} when the server answered with 2xx in time, {@code false}
     * for any error or timeout. Never signals an error.
     */
    public Mono<Boolean> healthcheck(String serverUrl) {
        WebClient webClient = webClientBuilder.baseUrl(serverUrl).build();

        return webClient.get()
                .uri("/healthcheck")
                .retrieve()
                .toBodilessEntity()
                .map(responseEntity -> responseEntity.getStatusCode().is2xxSuccessful())
                .timeout(healthcheckTimeout)
                .onErrorResume(error -> {
                    log.warn("Healthcheck failed for server: {} ({})", serverUrl, error.toString());
                    return Mono.just(false);
                });
    }
}
