package com.wordonline.matching.server.service;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.wordonline.matching.server.domain.Server;
import com.wordonline.matching.server.domain.ServerState;
import com.wordonline.matching.server.domain.ServerType;
import com.wordonline.matching.server.dto.RoomInfoDto;
import com.wordonline.matching.server.dto.RoomListDto;
import com.wordonline.matching.server.repository.ServerRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameServerService {

    private final ServerRepository serverRepository;
    private final WebClient.Builder webClientBuilder;

    public Mono<RoomListDto> getAllGameSessions() {
        return serverRepository.findAllByTypeAndState(ServerType.GAME, ServerState.ACTIVE)
                .flatMap(this::fetchGameSessionsFromServer)
                .collectList()
                .map(listOfLists -> {
                    List<RoomInfoDto> allRooms = listOfLists.stream()
                            .flatMap(List::stream)
                            .toList();
                    return new RoomListDto(allRooms);
                });
    }

    private Mono<List<RoomInfoDto>> fetchGameSessionsFromServer(Server server) {
        log.info("Fetching game sessions from server: {}", server.getServerUrl());
        
        WebClient webClient = webClientBuilder.baseUrl(server.getServerUrl()).build();
        
        return webClient.get()
                .uri("/api/server/game-sessions")
                .retrieve()
                .bodyToMono(RoomListDto.class)
                .map(roomListDto -> {
                    // Add server URL to each room info
                    return roomListDto.rooms().stream()
                            .map(room -> new RoomInfoDto(
                                    room.sessionId(),
                                    room.leftUserId(),
                                    room.rightUserId(),
                                    server.getServerUrl()
                            ))
                            .toList();
                })
                .onErrorResume(error -> {
                    log.error("Failed to fetch game sessions from server: {}", server.getServerUrl(), error);
                    return Mono.just(List.of());
                });
    }
}
