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
import com.wordonline.matching.global.service.LocalizationService;
import com.wordonline.matching.session.domain.SessionRecoveryInfo;
import com.wordonline.matching.session.dto.SimpleBooleanDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Slf4j
@Service
@RequiredArgsConstructor
public class LegacyGameMatchService {

    private final WebClient.Builder webClientBuilder;
    private final SessionRecoveryStore sessionRecoveryStore;
    private final LocalizationService localizationService;
    private final UserService userService;
    private final GameServerManagementService gameServerManagementService;

    public Mono<MatchedInfoDto> createSession(SessionDto sessionDto) {
        Optional<Server> optionalServer = gameServerManagementService.getAvailableServer();
        return getWebClient(optionalServer).flatMap(webClient ->
                getUserDetails(sessionDto.getUid1(), sessionDto.getUid2()).flatMap(tuple ->
                        webClient.post().uri("/api/server/game-sessions")
                                .body(Mono.just(sessionDto), SessionDto.class)
                                .accept(MediaType.APPLICATION_JSON)
                                .retrieve()
                                .bodyToMono(SimpleBooleanDto.class)
                                .map(SimpleBooleanDto::value)
                                .doOnNext(isSuccess -> log.info("Response from game server: {}", isSuccess))
                                .flatMap(isSuccess -> {
                                    if (!isSuccess) return getException();
                                    log.info("Session Created");
                                    MatchedInfoDto matchedInfoDto = new MatchedInfoDto(
                                            "Successfully Matched",
                                            optionalServer.get().getUrl(),
                                            tuple.getT1(),
                                            tuple.getT2(),
                                            sessionDto.getSessionId()
                                    );
                                    return sessionRecoveryStore.storeMatchInfo(matchedInfoDto)
                                            .thenReturn(matchedInfoDto);
                                })));
    }

    public Mono<MatchedInfoDto> getMatchInfo(long userId) {
        return sessionRecoveryStore.getSessionInfo(userId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Session Not Found")))
                .flatMap(sessionRecoveryInfo ->
                        checkSessionActive(sessionRecoveryInfo.serverUrl(), sessionRecoveryInfo.sessionId())
                                .flatMap(isActive -> {
                                    if (isActive) return mapToMatchedInfo(sessionRecoveryInfo);
                                    return Mono.error(new IllegalArgumentException("Session Already Deactivated"));
                                }));
    }

    private Mono<MatchedInfoDto> mapToMatchedInfo(SessionRecoveryInfo sessionRecoveryInfo) {
        return getUserDetails(sessionRecoveryInfo.leftUserId(), sessionRecoveryInfo.rightUserId())
                .map(tuple -> new MatchedInfoDto(sessionRecoveryInfo, tuple.getT1(), tuple.getT2()));
    }

    private Mono<Tuple2<UserDetailResponseDto, UserDetailResponseDto>> getUserDetails(long userId1, Long userId2) {
        return Mono.zip(
                userService.getUserDetail(userId1),
                userId2 != null ? userService.getUserDetail(userId2)
                        : Mono.just(new UserDetailResponseDto(0L, "dummy", "dummy@team6515.com"))
        );
    }

    private Mono<Boolean> checkSessionActive(String serverUrl, String sessionId) {
        return webClientBuilder.baseUrl(serverUrl).build()
                .get().uri("/api/server/game-sessions/" + sessionId + "/active")
                .retrieve()
                .bodyToMono(SimpleBooleanDto.class)
                .map(SimpleBooleanDto::value);
    }

    private <T> Mono<T> getException() {
        return Mono.deferContextual(ctx -> {
            LocaleContext localeContext = ctx.get(LocaleContext.class);
            return Mono.error(
                    new IllegalArgumentException(localizationService.getMessage(localeContext, "error.member.not.found")));
        });
    }

    private Mono<WebClient> getWebClient(Optional<Server> server) {
        return Mono.justOrEmpty(server)
                .switchIfEmpty(Mono.error(new IllegalStateException("No available server found")))
                .map(notNullServer -> webClientBuilder.baseUrl(notNullServer.getUrl()).build());
    }
}
