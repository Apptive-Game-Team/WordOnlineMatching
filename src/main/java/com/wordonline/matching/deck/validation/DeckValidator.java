package com.wordonline.matching.deck.validation;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.wordonline.matching.deck.dto.CardDto;
import com.wordonline.matching.deck.dto.CardType;
import com.wordonline.matching.deck.service.DeckDataService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class DeckValidator {

    private final DeckDataService deckDataService;
    public static final int DECK_CARD_COUNT = 15;
    public static final int LEAST_NUM_OF_MAGIC_CARD_TYPE = 3;
    public static final int LEAST_NUM_OF_TYPE_CARD_TYPE = 2;
    public static final int MAX_NUM_OF_SAME_CARD = 3;

    public Mono<Boolean> isValid(List<Long> cardIds) {
        if (cardIds == null || cardIds.size() != DECK_CARD_COUNT) {
            return Mono.just(false);
        }

        return deckDataService.getCardDtoMap()
                .map(cards -> {
                    Map<Long, Long> cardCounts = cardIds.stream()
                            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

                    if (cardCounts.values().stream().anyMatch(count -> count > MAX_NUM_OF_SAME_CARD)) {
                        return false;
                    }

                    if (!cards.keySet().containsAll(cardCounts.keySet())) {
                        return false;
                    }

                    long numOfType = cardCounts.keySet().stream()
                            .map(cards::get)
                            .filter(card -> card.type() == CardType.Type.Type)
                            .count();
                    long numOfMagic = cardCounts.keySet().stream()
                            .map(cards::get)
                            .filter(card -> card.type() == CardType.Type.Magic)
                            .count();

                    return numOfType >= LEAST_NUM_OF_TYPE_CARD_TYPE
                            && numOfMagic >= LEAST_NUM_OF_MAGIC_CARD_TYPE;
                });
    }
}
