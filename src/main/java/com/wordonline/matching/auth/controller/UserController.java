package com.wordonline.matching.auth.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wordonline.matching.auth.dto.UserResponseDto;
import com.wordonline.matching.auth.service.UserId;
import com.wordonline.matching.auth.service.UserService;
import com.wordonline.matching.matching.dto.MatchedInfoDto;
import com.wordonline.matching.quest.dto.QuestCheckResponseDto;
import com.wordonline.matching.quest.service.QuestService;
import com.wordonline.matching.session.service.LegacyGameMatchService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@RequestMapping("/api/users")
@Slf4j
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final LegacyGameMatchService gameMatchService;
    private final QuestService questService;

    @GetMapping("/mine")
    public Mono<UserResponseDto> getUser(@UserId Long userId) {
        return userService.getUser(userId);
    }

    @DeleteMapping("/mine")
    public Mono<ResponseEntity<String>> deleteUser(@UserId Long userId) {
        return userService.deleteUser(userId)
                .then(Mono.just(ResponseEntity.ok("successfully delete")))
                .onErrorResume(ex -> Mono.just(new ResponseEntity<>(HttpStatus.NOT_FOUND)));
    }

    /**
     * @param wait seconds to hold the request open until the status changes. 0 answers immediately,
     *             which is what clients that do not know about long-polling keep doing.
     */
    @GetMapping("/mine/status")
    public Mono<Map<String, String>> getMyStatus(
            @UserId Long userId,
            @RequestParam(name = "wait", defaultValue = "0") long wait
    ) {
        return userService.awaitStatus(userId, wait)
                .map(status -> Map.of("status", status.name()));
    }

    @GetMapping("/mine/match-info")
    public Mono<MatchedInfoDto> getMatchInfo(
            @UserId Long userId
    ) {

        return gameMatchService.getMatchInfo(
                userId
        );
    }

    @PostMapping("/mine/quests/check")
    public Mono<QuestCheckResponseDto> checkMyQuests(
            @UserId Long userId
    ) {
        return questService.checkQuestsWithRewards(
                userId
        ).map(QuestCheckResponseDto::new);
    }
}
