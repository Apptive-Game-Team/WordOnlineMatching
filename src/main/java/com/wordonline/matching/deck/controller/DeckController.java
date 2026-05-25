package com.wordonline.matching.deck.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wordonline.matching.auth.service.UserId;
import com.wordonline.matching.deck.dto.CardPoolDto;
import com.wordonline.matching.deck.dto.DeckRequestDto;
import com.wordonline.matching.deck.dto.DeckResponseDto;
import com.wordonline.matching.deck.service.DeckService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@PreAuthorize("isAuthenticated()")
@RequestMapping("/api/users/mine")
@RestController
@RequiredArgsConstructor
public class DeckController {

    private final DeckService deckService;

    @GetMapping("/cards")
    public Mono<CardPoolDto> getCardPool(
            @UserId Long userId
    ) {
        return deckService.getCardPool(userId);
    }

    @GetMapping("/decks")
    public Flux<DeckResponseDto> getDecks(
            @UserId Long userId
    ) {
        return deckService.getDecks(userId);
    }

    @PostMapping("/decks")
    public Mono<DeckResponseDto> saveDeck(
            @Validated @RequestBody DeckRequestDto deckRequestDto,
            @UserId Long userId
            ) {
        return deckService.saveDeck(userId, deckRequestDto);
    }

    @PutMapping("/decks/{deckId}")
    public Mono<DeckResponseDto> updateDeck(
            @PathVariable Long deckId,
            @Validated @RequestBody DeckRequestDto deckRequestDto,
            @UserId Long userId
    ) {
        return deckService.updateDeck(
                userId,
                deckId,
                deckRequestDto);
    }

    @PostMapping("/decks/{deckId}")
    public Mono<String> selectDeck(
            @PathVariable Long deckId,
            @UserId Long userId
    ) {
        return deckService.selectDeck(
                userId,
                deckId
        ).then(Mono.just("Successfully Saved"));
    }

    @DeleteMapping("/decks/{deckId}")
    public Mono<String> deleteDeck(
            @PathVariable Long deckId,
            @UserId Long userId
    ) {
        return deckService.deleteDeck(userId, deckId)
                .then(Mono.just("Successfully Deleted"));
    }
}
