package com.wordonline.matching.deck.service;

import org.springframework.context.i18n.LocaleContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wordonline.matching.auth.repository.UserRepository;
import com.wordonline.matching.deck.domain.Deck;
import com.wordonline.matching.deck.domain.DeckCard;
import com.wordonline.matching.deck.domain.UserCard;
import com.wordonline.matching.deck.repository.DeckCardRepository;
import com.wordonline.matching.deck.repository.DeckRepository;
import com.wordonline.matching.deck.repository.UserCardRepository;
import com.wordonline.matching.service.LocalizationService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class DeckInitializer {

    private final DeckDataService deckDataService;
    private final UserCardRepository userCardRepository;
    private final UserRepository userRepository;
    private final DeckRepository deckRepository;
    private final LocalizationService localizationService;
    private final DeckCardRepository deckCardRepository;

    @Transactional
    public Mono<Long> initializeCard(long userId) {
        return giveStarterCard(userId).then(
                giveDefaultDeck(userId));
    }

    private Mono<Void> giveStarterCard(long userId) {
        return deckDataService.getAllCard()
                .flatMapMany(Flux::fromIterable)
                .flatMap(card -> userCardRepository.save(new UserCard(userId, card.getId(), 3)))
                .then();
    }

    private Mono<Long> giveDefaultDeck(long userId) {
        return Mono.deferContextual(ctx -> {
                    LocaleContext localeContext = ctx.get(LocaleContext.class);
                    String defaultName = localizationService.getMessage(localeContext, "string.default.deck");
                    return Mono.just(new Deck(userId, defaultName));
                }).flatMap(deckRepository::save)
                .flatMap(deck ->
                        Flux.range(1, 9)
                                .map(Integer::longValue)
                                .flatMap(cardId -> {
                                    int count = (cardId == 6L) ? 2 : 1;
                                    return deckCardRepository.save(new DeckCard(deck.getId(), cardId, count));
                                })
                                .then(Mono.just(deck))
                )
                .flatMap(deck ->
                        userRepository.updateSelectedDeck(userId, deck.getId())
                                .then(Mono.just(deck.getId()))
                );
    }
}
