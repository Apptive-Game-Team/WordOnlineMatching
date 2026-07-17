package com.wordonline.matching.matching.client;

import com.wordonline.matching.global.service.LocalizationService;
import com.wordonline.matching.matching.dto.AccountMemberResponseDto;
import com.wordonline.matching.matching.service.BotMemberMaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountClientTest {

    @Mock
    private WebClient.Builder builder;
    @Mock
    private WebClient webClient;
    @Mock
    private WebClient.RequestHeadersUriSpec<?> request;
    @Mock
    private WebClient.ResponseSpec response;
    @Mock
    private LocalizationService localizationService;
    @Mock
    private BotMemberMaker botMemberMaker;

    private AccountClient accountClient;

    @BeforeEach
    void setUp() {
        when(builder.baseUrl("http://account")).thenReturn(builder);
        when(builder.build()).thenReturn(webClient);
        accountClient = new AccountClient(builder, "http://account", localizationService, botMemberMaker);
    }

    @Test
    void negativeIdUsesBotPersonaWithoutAccountRequest() {
        var bot = new AccountMemberResponseDto("bot@team6515.com", "persona name");
        when(botMemberMaker.getBot(-5L)).thenReturn(Mono.just(bot));

        StepVerifier.create(accountClient.getMember(-5L))
                .expectNext(bot)
                .verifyComplete();

        verify(webClient, never()).get();
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void positiveIdKeepsAccountServerRequest() {
        var human = new AccountMemberResponseDto("human@example.com", "human");
        WebClient.RequestHeadersUriSpec rawRequest = request;
        when(webClient.get()).thenReturn(rawRequest);
        when(rawRequest.uri("/api/members/10")).thenReturn(rawRequest);
        when(rawRequest.retrieve()).thenReturn(response);
        when(response.onStatus(any(), any())).thenReturn(response);
        when(response.bodyToMono(any(ParameterizedTypeReference.class))).thenReturn(Mono.just(human));

        StepVerifier.create(accountClient.getMember(10L))
                .expectNext(human)
                .verifyComplete();

        verify(botMemberMaker, never()).getBot(anyLong());
    }
}
