package com.wordonline.matching.auth.controller;

import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wordonline.matching.auth.dto.UserResponseDto;
import com.wordonline.matching.auth.dto.UserStatisticsGamesResponseDto;
import com.wordonline.matching.auth.dto.UserStatisticsOverviewResponseDto;
import com.wordonline.matching.auth.service.UserId;
import com.wordonline.matching.auth.service.UserService;
import com.wordonline.matching.auth.service.UserStatisticsService;
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
    private final UserStatisticsService userStatisticsService;
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

    @GetMapping("/mine/status")
    public Mono<Map<String, String>> getMyStatus(
            @UserId Long userId
    ) {
        return userService.getStatus(userId)
                .map(status -> Map.of("status", status.name()));
    }

    @GetMapping("/mine/statistics/overview")
    public Mono<UserStatisticsOverviewResponseDto> getMyStatisticsOverview(
            @UserId Long userId
    ) {
        return userStatisticsService.getOverview(userId);
    }

    @GetMapping("/mine/statistics/games")
    public Mono<UserStatisticsGamesResponseDto> getMyStatisticsGames(
            @UserId Long userId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return userStatisticsService.getGames(userId, pageable);
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
