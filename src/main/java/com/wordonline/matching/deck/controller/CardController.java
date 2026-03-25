package com.wordonline.matching.deck.controller;

import com.wordonline.matching.auth.service.UserId;
import com.wordonline.matching.deck.dto.CardListResponse;
import com.wordonline.matching.deck.service.CardListService;
import com.wordonline.matching.quest.dto.QuestProgressResponseDto;
import com.wordonline.matching.quest.service.QuestService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class CardController {

    private final CardListService cardListService;
    private final QuestService questService;

    public CardController(CardListService cardListService, QuestService questService) {
        this.cardListService = cardListService;
        this.questService = questService;
    }

    @GetMapping("/api/users/mine/cardLists")
    public Mono<CardListResponse> getMyCards(@UserId Long userId) {
        return cardListService.getMyCards(userId);
    }

    @GetMapping("/api/cards/{cardId}/quest-progress")
    public Mono<QuestProgressResponseDto> getQuestProgressByCard(@UserId Long userId, @PathVariable long cardId) {
        return questService.findQuestProgressByCard(userId, cardId);
    }
}
