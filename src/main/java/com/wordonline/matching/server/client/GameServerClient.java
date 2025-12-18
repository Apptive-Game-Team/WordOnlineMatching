package com.wordonline.matching.server.client;

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
}
