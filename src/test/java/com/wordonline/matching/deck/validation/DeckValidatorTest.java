package com.wordonline.matching.deck.validation;

import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.wordonline.matching.deck.dto.CardDto;
import com.wordonline.matching.deck.dto.CardType;
import com.wordonline.matching.deck.service.DeckDataService;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class DeckValidatorTest {

    @Mock
    private DeckDataService deckDataService;

    @InjectMocks
    private DeckValidator deckValidator;

    @Test
    void isValid_ReturnsTrue_WhenDeckSatisfiesStandard() {
        when(deckDataService.getCardDtoMap()).thenReturn(Mono.just(cardMap()));

        StepVerifier.create(deckValidator.isValid(List.of(
                        1L, 1L, 2L, 3L, 3L, 3L, 4L, 5L, 5L,
                        6L, 7L, 8L, 9L, 10L, 11L
                )))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void isValid_ReturnsFalse_WhenDeckSizeIsNotFifteen() {
        StepVerifier.create(deckValidator.isValid(List.of(1L, 2L)))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void isValid_ReturnsFalse_WhenSameCardIsMoreThanThree() {
        when(deckDataService.getCardDtoMap()).thenReturn(Mono.just(cardMap()));

        StepVerifier.create(deckValidator.isValid(List.of(
                        1L, 1L, 1L, 1L, 2L, 3L, 4L, 5L, 5L,
                        6L, 7L, 8L, 9L, 10L, 11L
                )))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void isValid_ReturnsFalse_WhenMagicTypesAreLessThanThree() {
        when(deckDataService.getCardDtoMap()).thenReturn(Mono.just(cardMap()));

        StepVerifier.create(deckValidator.isValid(List.of(
                        1L, 1L, 1L, 2L, 2L, 2L, 6L, 6L, 6L,
                        7L, 7L, 7L, 8L, 8L, 8L
                )))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void isValid_ReturnsFalse_WhenAttributeTypesAreLessThanTwo() {
        when(deckDataService.getCardDtoMap()).thenReturn(Mono.just(cardMap()));

        StepVerifier.create(deckValidator.isValid(List.of(
                        1L, 1L, 1L, 2L, 2L, 2L, 3L, 3L, 3L,
                        4L, 4L, 4L, 6L, 6L, 6L
                )))
                .expectNext(false)
                .verifyComplete();
    }

    private Map<Long, CardDto> cardMap() {
        return Map.ofEntries(
                Map.entry(1L, new CardDto(1L, CardType.Shoot)),
                Map.entry(2L, new CardDto(2L, CardType.Explode)),
                Map.entry(3L, new CardDto(3L, CardType.Spawn)),
                Map.entry(4L, new CardDto(4L, CardType.Drop)),
                Map.entry(5L, new CardDto(5L, CardType.Build)),
                Map.entry(6L, new CardDto(6L, CardType.Fire)),
                Map.entry(7L, new CardDto(7L, CardType.Water)),
                Map.entry(8L, new CardDto(8L, CardType.Lightning)),
                Map.entry(9L, new CardDto(9L, CardType.Nature)),
                Map.entry(10L, new CardDto(10L, CardType.Rock)),
                Map.entry(11L, new CardDto(11L, CardType.Wind))
        );
    }
}
