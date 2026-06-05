package com.wordonline.matching.deck.controller;

import com.wordonline.matching.deck.service.CardListService;
import com.wordonline.matching.quest.domain.QuestState;
import com.wordonline.matching.quest.dto.QuestProgressResponseDto;
import com.wordonline.matching.quest.service.QuestService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import com.wordonline.matching.auth.service.UserIdResolver;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@WebFluxTest(CardController.class)
@Import(UserIdResolver.class)
class CardControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private CardListService cardListService;

    @MockitoBean
    private QuestService questService;

    @Test
    @DisplayName("카드의_퀘스트_진행상황_조회_엔드포인트_호출_성공")
    void getQuestProgressByCard_Success() {
        long userId = 1L;
        long cardId = 1L;
        int progress = 50;
        int requireValue = 100;

        QuestProgressResponseDto mockResponse = new QuestProgressResponseDto(QuestState.IN_PROGRESS, progress, requireValue);

        when(questService.findQuestProgressByCard(userId, cardId)).thenReturn(Mono.just(mockResponse));

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("memberId", userId)))
                .get()
                .uri("/api/cards/{cardId}/quest-progress", cardId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(QuestProgressResponseDto.class)
                .value(response -> {
                    assert response.getState() == QuestState.IN_PROGRESS;
                    assert response.getProgress() == progress;
                    assert response.getRequireValue() == requireValue;
                });
    }
}
