package com.wordonline.matching.deck.validation;

import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.wordonline.matching.deck.domain.UserCard;
import com.wordonline.matching.deck.dto.CardDto;
import com.wordonline.matching.deck.dto.CardType;
import com.wordonline.matching.deck.repository.UserCardRepository;
import com.wordonline.matching.deck.service.DeckDataService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class DeckValidatorTest {

    private static final long USER_ID = 1L;

    @Mock
    private DeckDataService deckDataService;

    @Mock
    private UserCardRepository userCardRepository;

    @InjectMocks
    private DeckValidator deckValidator;

    @Test
    void isValid_ReturnsTrue_WhenDeckSatisfiesStandard() {
        stubCards();
        stubOwnedCards(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L);

        StepVerifier.create(deckValidator.isValid(USER_ID, List.of(
                        1L, 1L, 2L, 3L, 3L, 3L, 4L, 5L, 5L,
                        6L, 7L, 8L, 9L, 10L, 11L
                )))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void isValid_ReturnsFalse_WhenDeckSizeIsNotFifteen() {
        StepVerifier.create(deckValidator.isValid(USER_ID, List.of(1L, 2L)))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void isValid_ReturnsFalse_WhenSameCardIsMoreThanThree() {
        StepVerifier.create(deckValidator.isValid(USER_ID, List.of(
                        1L, 1L, 1L, 1L, 2L, 3L, 4L, 5L, 5L,
                        6L, 7L, 8L, 9L, 10L, 11L
                )))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void isValid_ReturnsFalse_WhenMagicTypesAreLessThanThree() {
        stubCards();
        stubOwnedCards(1L, 2L, 6L, 7L, 8L);

        StepVerifier.create(deckValidator.isValid(USER_ID, List.of(
                        1L, 1L, 1L, 2L, 2L, 2L, 6L, 6L, 6L,
                        7L, 7L, 7L, 8L, 8L, 8L
                )))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void isValid_ReturnsFalse_WhenAttributeTypesAreLessThanTwo() {
        stubCards();
        stubOwnedCards(1L, 2L, 3L, 4L, 6L);

        StepVerifier.create(deckValidator.isValid(USER_ID, List.of(
                        1L, 1L, 1L, 2L, 2L, 2L, 3L, 3L, 3L,
                        4L, 4L, 4L, 6L, 6L, 6L
                )))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void isValid_ReturnsFalse_WhenCardIsNotOwned() {
        stubCards();
        stubOwnedCards(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);

        StepVerifier.create(deckValidator.isValid(USER_ID, List.of(
                        1L, 1L, 2L, 3L, 3L, 3L, 4L, 5L, 5L,
                        6L, 7L, 8L, 9L, 10L, 11L
                )))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void isValid_ReturnsFalse_WhenOwnedCountIsLessThanRequested() {
        stubCards();
        when(userCardRepository.findAllByUserId(USER_ID)).thenReturn(Flux.just(
                new UserCard(USER_ID, 1L, 3),
                new UserCard(USER_ID, 2L, 3),
                new UserCard(USER_ID, 3L, 1),
                new UserCard(USER_ID, 4L, 3),
                new UserCard(USER_ID, 5L, 3),
                new UserCard(USER_ID, 6L, 3),
                new UserCard(USER_ID, 7L, 3),
                new UserCard(USER_ID, 8L, 3),
                new UserCard(USER_ID, 9L, 3),
                new UserCard(USER_ID, 10L, 3),
                new UserCard(USER_ID, 11L, 3)
        ));

        StepVerifier.create(deckValidator.isValid(USER_ID, List.of(
                        1L, 1L, 2L, 3L, 3L, 3L, 4L, 5L, 5L,
                        6L, 7L, 8L, 9L, 10L, 11L
                )))
                .expectNext(false)
                .verifyComplete();
    }

    private void stubCards() {
        when(deckDataService.getCardDtoMap()).thenReturn(Mono.just(cardMap()));
    }

    private void stubOwnedCards(Long... cardIds) {
        when(userCardRepository.findAllByUserId(USER_ID)).thenReturn(
                Flux.fromArray(cardIds).map(cardId -> new UserCard(USER_ID, cardId, 3)));
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
