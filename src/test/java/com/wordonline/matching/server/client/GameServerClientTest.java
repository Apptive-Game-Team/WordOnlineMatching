package com.wordonline.matching.server.client;

import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class GameServerClientTest {

    private static final String SERVER_URL = "http://game:9090";

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
        when(builder.baseUrl(SERVER_URL)).thenReturn(builder);
        when(builder.build()).thenReturn(webClient);
        gameServerClient = new GameServerClient(builder, 50L);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void stubHealthcheckResponse(Mono<ResponseEntity<Void>> bodilessEntity) {
        WebClient.RequestHeadersUriSpec rawRequest = request;
        when(webClient.get()).thenReturn(rawRequest);
        when(rawRequest.uri("/healthcheck")).thenReturn(rawRequest);
        when(rawRequest.retrieve()).thenReturn(response);
        when(response.toBodilessEntity()).thenReturn(bodilessEntity);
    }

    @Test
    void 응답이_2xx면_true를_반환한다() {
        stubHealthcheckResponse(Mono.just(ResponseEntity.ok().build()));

        StepVerifier.create(gameServerClient.healthcheck(SERVER_URL))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void 타임아웃이면_예외를_전파하지_않고_false를_반환한다() {
        // never responds -> the configured 50ms timeout fires
        stubHealthcheckResponse(Mono.never());

        StepVerifier.create(gameServerClient.healthcheck(SERVER_URL))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void 연결_오류면_false를_반환한다() {
        stubHealthcheckResponse(Mono.error(new IllegalStateException("connection refused")));

        StepVerifier.create(gameServerClient.healthcheck(SERVER_URL))
                .expectNext(false)
                .verifyComplete();
    }
}
