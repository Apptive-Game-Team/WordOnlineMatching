package com.wordonline.matching.session.service;

import java.util.Optional;

import org.springframework.context.i18n.LocaleContext;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.wordonline.matching.auth.dto.UserDetailResponseDto;
import com.wordonline.matching.auth.service.UserService;
import com.wordonline.matching.matching.dto.MatchedInfoDto;
import com.wordonline.matching.matching.dto.SessionDto;
import com.wordonline.matching.server.entity.Server;
import com.wordonline.matching.server.service.GameServerManagementService;
import com.wordonline.matching.service.LocalizationService;
import com.wordonline.matching.session.domain.SessionRecoveryInfo;
import com.wordonline.matching.session.dto.SimpleBooleanDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameMatchService {

    private final WebClient.Builder webClientBuilder;
    private final SessionRecoveryStore sessionRecoveryStore;
    private final LocalizationService localizationService;
    private final UserService userService;
    private final GameServerManagementService gameServerManagementService;

    public Mono<MatchedInfoDto> createSession(SessionDto sessionDto) {
        Optional<Server> optionalServer = gameServerManagementService.getAvailableServer();
        return getWebClient(optionalServer).flatMap(webClient -> webClient.post().uri("/api/server/game-sessions")
                .body(Mono.just(sessionDto), SessionDto.class)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(SimpleBooleanDto.class)
                .map(SimpleBooleanDto::value)
                .doOnNext(isSuccess -> log.info("Response from game server: {}", isSuccess))
                .flatMap(isSuccess -> {
                        if (!isSuccess) {
                            return getException();
                        }
                        log.info("Session Created");
                        return getUserDetails(sessionDto.uid1(), sessionDto.uid2());
                })
                .map(tuple ->
                {
                    MatchedInfoDto matchedInfoDto = new MatchedInfoDto(
                            "Successfully Matched",
                            optionalServer.get().getUrl(),
                            tuple.getT1(),
                            tuple.getT2(),
                            sessionDto.sessionId()
                    );
                    sessionRecoveryStore.storeMatchInfo(matchedInfoDto);
                    return matchedInfoDto;
                }));
    }

    public Mono<MatchedInfoDto> getMatchInfo(long userId) {
        SessionRecoveryInfo sessionRecoveryInfo = sessionRecoveryStore.getSessionInfo(userId);
        if (sessionRecoveryInfo == null) {
            return Mono.error(new IllegalArgumentException("Session Not Found"));
        }
        return checkSessionActive(sessionRecoveryInfo.sessionId())
                .flatMap(isActive -> {
                    if (isActive) {
                        return mapToMatchedInfo(sessionRecoveryInfo);
                    }
                    return Mono.error(new IllegalArgumentException("Session Already Deactivated"));
                });
    }

    private Mono<MatchedInfoDto> mapToMatchedInfo(SessionRecoveryInfo sessionRecoveryInfo) {
        return getUserDetails(sessionRecoveryInfo.leftUserId(), sessionRecoveryInfo.rightUserId())
                .map(tuple ->
                        new MatchedInfoDto(
                                sessionRecoveryInfo,
                                tuple.getT1(),
                                tuple.getT2()
                        ));
    }

    private Mono<Tuple2<UserDetailResponseDto, UserDetailResponseDto>> getUserDetails(long userId1, long userId2) {
        return Mono.zip(
                userService.getUserDetail(userId1),
                userService.getUserDetail(userId2)
        );
    }

    private Mono<Boolean> checkSessionActive(String sessionId) {
        return getWebClient().flatMap(webClient ->
                webClient.get().uri("/api/server/game-sessions/" + sessionId + "/active")
                    .retrieve()
                    .bodyToMono(SimpleBooleanDto.class)
                    .map(SimpleBooleanDto::value));
    }

    private <T> Mono<T> getException() {
        return Mono.deferContextual(ctx -> {
            LocaleContext localeContext = ctx.get(LocaleContext.class);
            return Mono.error(
                    new IllegalArgumentException(localizationService.getMessage(localeContext, "error.member.not.found")));
        });
    }

    private Mono<WebClient> getWebClient() {
        return getWebClient(gameServerManagementService.getAvailableServer());
    }

    private Mono<WebClient> getWebClient(Optional<Server> server) {
        return Mono.justOrEmpty(server)
                .switchIfEmpty(Mono.error(new IllegalStateException("No available server found")))
                .map(notNullserver -> webClientBuilder.baseUrl(notNullserver.getUrl()).build());
    }
}
