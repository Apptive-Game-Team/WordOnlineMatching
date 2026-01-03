package com.wordonline.matching.deck.controller;

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
    public Mono<CardListResponse> getMyCards(@AuthenticationPrincipal Jwt jwt) {
        var userId = Long.parseLong(jwt.getClaimAsString("memberId"));
        return cardListService.getMyCards(userId);
    }

    @GetMapping("/api/cards/{cardId}/quest-progress")
    public Mono<QuestProgressResponseDto> getQuestProgressByCard(@AuthenticationPrincipal Jwt jwt, @PathVariable long cardId) {
        long userId = Long.parseLong(jwt.getClaimAsString("memberId"));
        return questService.findQuestProgressByCard(userId, cardId);
    }
}
