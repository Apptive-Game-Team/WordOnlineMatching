package com.wordonline.matching.session.service;

import com.wordonline.matching.auth.dto.UserDetailResponseDto;
import com.wordonline.matching.auth.service.UserService;
import com.wordonline.matching.global.service.LocalizationService;
import com.wordonline.matching.matching.domain.MatchTicket;
import com.wordonline.matching.matching.domain.MatchTicketState;
import com.wordonline.matching.matching.dto.MatchedInfoDto;
import com.wordonline.matching.matching.dto.SessionDto;
import com.wordonline.matching.matching.repository.MatchTicketRepository;
import com.wordonline.matching.server.entity.Server;
import com.wordonline.matching.server.service.GameServerManagementService;
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

import java.time.Instant;
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
    private MatchTicketRepository matchTicketRepository;
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
                webClientBuilder, matchTicketRepository, localizationService, userService, gameServerManagementService);
    }

    @Test
    void 매치_정보는_티켓에서_복구되고_게임_서버를_호출하지_않는다() {
        MatchTicket ticket = new MatchTicket(
                1L, 1200L, MatchTicketState.PLAYING, Instant.now(), Instant.now(),
                "session-1", "http://server-b:8080", 1L, 2L, 0);
        when(matchTicketRepository.findById(1L)).thenReturn(Mono.just(ticket));
        when(userService.getUserDetail(1L)).thenReturn(Mono.just(new UserDetailResponseDto(1L, "left", "l@x.com")));
        when(userService.getUserDetail(2L)).thenReturn(Mono.just(new UserDetailResponseDto(2L, "right", "r@x.com")));

        StepVerifier.create(legacyGameMatchService.getMatchInfo(1L))
                .assertNext(info -> {
                    assertEquals("http://server-b:8080", info.getServer());
                    assertEquals("session-1", info.getSessionId());
                })
                .verifyComplete();

        assertTrue(sentRequests.isEmpty());
    }

    @Test
    void 아직_세션이_확정되지_않은_티켓은_매치_정보가_없다() {
        MatchTicket ticket = new MatchTicket(
                1L, 1200L, MatchTicketState.QUEUED, Instant.now(), Instant.now(),
                null, null, null, null, 0);
        when(matchTicketRepository.findById(1L)).thenReturn(Mono.just(ticket));

        StepVerifier.create(legacyGameMatchService.getMatchInfo(1L))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void 세션이_만들어지면_양쪽_티켓을_PLAYING으로_기록한다() {
        Server server = mock(Server.class);
        when(server.getUrl()).thenReturn("http://server-a:8080");
        when(gameServerManagementService.getAvailableServer()).thenReturn(Optional.of(server));
        when(userService.getUserDetail(1L)).thenReturn(Mono.just(new UserDetailResponseDto(1L, "left", "l@x.com")));
        when(userService.getUserDetail(2L)).thenReturn(Mono.just(new UserDetailResponseDto(2L, "right", "r@x.com")));
        when(matchTicketRepository.markPlaying(1L, "session-1", "http://server-a:8080", 1L, 2L))
                .thenReturn(Mono.just(1L));
        when(matchTicketRepository.markPlaying(2L, "session-1", "http://server-a:8080", 1L, 2L))
                .thenReturn(Mono.just(1L));

        StepVerifier.create(legacyGameMatchService.createSession(SessionDto.Companion.PVP("session-1", 1L, 2L)))
                .assertNext(info -> assertEquals(MatchedInfoDto.class, info.getClass()))
                .verifyComplete();
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
