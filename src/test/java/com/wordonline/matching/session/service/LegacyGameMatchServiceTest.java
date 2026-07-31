package com.wordonline.matching.session.service;

import com.wordonline.matching.auth.dto.UserDetailResponseDto;
import com.wordonline.matching.auth.service.UserService;
import com.wordonline.matching.global.service.LocalizationService;
import com.wordonline.matching.matching.dto.SessionDto;
import com.wordonline.matching.server.entity.Server;
import com.wordonline.matching.server.service.GameServerManagementService;
import com.wordonline.matching.session.domain.SessionRecoveryInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyGameMatchServiceTest {

    @Mock
    private SessionRecoveryStore sessionRecoveryStore;
    @Mock
    private LocalizationService localizationService;
    @Mock
    private UserService userService;
    @Mock
    private GameServerManagementService gameServerManagementService;

    private final List<ClientRequest> sentRequests = new ArrayList<>();
    private LegacyGameMatchService legacyGameMatchService;

    @BeforeEach
    void setUp() {
        WebClient.Builder webClientBuilder = WebClient.builder().exchangeFunction(request -> {
            sentRequests.add(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"value\":true}")
                    .build());
        });
        legacyGameMatchService = new LegacyGameMatchService(
                webClientBuilder, sessionRecoveryStore, localizationService, userService, gameServerManagementService);
    }

    @Test
    void 세션이_생성된_게임_서버로_활성_여부를_조회한다() {
        SessionRecoveryInfo recoveryInfo = new SessionRecoveryInfo(
                1L, 2L, "session-1", "http://server-b:8080", System.currentTimeMillis() + 60_000);
        when(sessionRecoveryStore.getSessionInfo(1L)).thenReturn(Mono.just(recoveryInfo));
        when(userService.getUserDetail(1L)).thenReturn(Mono.just(new UserDetailResponseDto(1L, "left", "l@x.com")));
        when(userService.getUserDetail(2L)).thenReturn(Mono.just(new UserDetailResponseDto(2L, "right", "r@x.com")));

        StepVerifier.create(legacyGameMatchService.getMatchInfo(1L))
                .expectNextCount(1)
                .verifyComplete();

        assertEquals("http://server-b:8080/api/server/game-sessions/session-1/active",
                sentRequests.get(0).url().toString());
    }

    @Test
    void 유저_조회에_실패하면_게임_서버에_방을_만들지_않는다() {
        Server server = mock(Server.class);
        when(server.getUrl()).thenReturn("http://server-a:8080");
        when(gameServerManagementService.getAvailableServer()).thenReturn(Optional.of(server));
        when(userService.getUserDetail(1L)).thenReturn(Mono.error(new IllegalStateException("account server down")));
        when(userService.getUserDetail(2L)).thenReturn(Mono.just(new UserDetailResponseDto(2L, "right", "r@x.com")));

        StepVerifier.create(legacyGameMatchService.createSession(SessionDto.Companion.PVP("session-1", 1L, 2L)))
                .expectError(IllegalStateException.class)
                .verify();

        assertTrue(sentRequests.isEmpty());
    }
}
