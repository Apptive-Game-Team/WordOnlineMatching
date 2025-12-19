package com.wordonline.matching.server.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wordonline.matching.server.dto.RoomListDto;
import com.wordonline.matching.server.service.GameSessionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/game-sessions")
@RequiredArgsConstructor
public class GameServerController {

    private final GameSessionService gameSessionService;

    @GetMapping
    public Mono<RoomListDto> getAllGameSessions() {
        log.info("Fetching all game sessions from all active game servers");
        return gameSessionService.getAllGameSessions();
    }
}
