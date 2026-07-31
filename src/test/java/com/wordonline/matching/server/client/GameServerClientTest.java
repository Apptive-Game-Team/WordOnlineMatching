package com.wordonline.matching.server.client;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import com.wordonline.matching.server.dto.RoomListDto;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameServerClientTest {

    @Mock
    private WebClient.Builder builder;
    @Mock
    private WebClient webClient;
    @Mock
    private WebClient.RequestHeadersUriSpec<?> request;
    @Mock
    private WebClient.ResponseSpec response;

    private GameServerClient gameServerClient;

    @BeforeEach
    void setUp() {
        when(builder.baseUrl("http://game")).thenReturn(builder);
        when(builder.build()).thenReturn(webClient);
        gameServerClient = new GameServerClient(builder);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void 응답하지_않는_게임_서버는_타임아웃_후_빈_방_목록을_반환한다() {
        WebClient.RequestHeadersUriSpec rawRequest = request;
        when(webClient.get()).thenReturn(rawRequest);
        when(rawRequest.uri("/api/server/game-sessions")).thenReturn(rawRequest);
        when(rawRequest.retrieve()).thenReturn(response);
        when(response.bodyToMono(RoomListDto.class)).thenReturn(Mono.never());

        StepVerifier.withVirtualTime(() -> gameServerClient.getGameSessions("http://game"))
                .thenAwait(Duration.ofSeconds(5))
                .expectNext(new RoomListDto(List.of()))
                .verifyComplete();
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void 응답이_없는_서버는_타임아웃되면_false를_반환한다() {
        WebClient.RequestHeadersUriSpec rawRequest = request;
        when(webClient.get()).thenReturn(rawRequest);
        when(rawRequest.uri("/healthcheck")).thenReturn(rawRequest);
        when(rawRequest.retrieve()).thenReturn(response);
        when(response.toBodilessEntity()).thenReturn(Mono.never());

        StepVerifier.withVirtualTime(() -> gameServerClient.healthcheck("http://game"))
                .thenAwait(Duration.ofSeconds(3))
                .expectNext(false)
                .verifyComplete();
    }
}
