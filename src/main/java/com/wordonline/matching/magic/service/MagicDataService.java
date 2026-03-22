package com.wordonline.matching.magic.service;

import com.wordonline.matching.deck.domain.Card;
import com.wordonline.matching.deck.repository.CardRepository;
import com.wordonline.matching.magic.domain.Magic;
import com.wordonline.matching.magic.domain.MagicCard;
import com.wordonline.matching.magic.dto.MagicDto;
import com.wordonline.matching.magic.dto.MagicsResponse;
import com.wordonline.matching.magic.repository.MagicCardRepository;
import com.wordonline.matching.magic.repository.MagicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
@Transactional
public class MagicDataService {

    private final MagicCardRepository magicCardRepository;
    private final MagicRepository magicRepository;
    private final CardRepository cardRepository;

    @Transactional(readOnly = true)
    public Mono<MagicsResponse> getMagics(String currentVersion) {
        Flux<MagicCard> magicCardsFlux;
        String initialVersion = currentVersion;

        if (currentVersion == null || currentVersion.isEmpty()) {
            magicCardsFlux = magicCardRepository.findAll();
        } else {
            LocalDateTime timestamp = LocalDateTime.parse(currentVersion, DateTimeFormatter.ISO_DATE_TIME);
            magicCardsFlux = magicCardRepository.findAllByUpdatedMagicsSince(timestamp);
        }

        return magicCardsFlux
                .collectList()
                .flatMap(magicCards -> {
                    if (magicCards.isEmpty()) {
                        return Mono.just(new MagicsResponse(initialVersion, List.of()));
                    }

                    List<Long> magicIds = magicCards.stream()
                            .map(MagicCard::getMagicId)
                            .distinct()
                            .collect(Collectors.toList());

                    List<Long> cardIds = magicCards.stream()
                            .map(MagicCard::getCardId)
                            .distinct()
                            .collect(Collectors.toList());

                    Mono<Map<Long, Magic>> magicsMono = magicRepository.findAllById(magicIds)
                            .collectMap(Magic::getId);

                    Mono<Map<Long, Card>> cardsMono = cardRepository.findAllById(cardIds)
                            .collectMap(Card::getId);

                    return Mono.zip(magicsMono, cardsMono)
                            .map(tuple -> {
                                Map<Long, Magic> magicMap = tuple.getT1();
                                Map<Long, Card> cardMap = tuple.getT2();

                                Map<Long, List<MagicCard>> cardsByMagicId = magicCards.stream()
                                        .collect(Collectors.groupingBy(MagicCard::getMagicId));

                                LocalDateTime maxUpdatedAt = magicCards.stream()
                                        .map(MagicCard::getUpdatedAt)
                                        .filter(Objects::nonNull)
                                        .max(Comparator.naturalOrder())
                                        .orElse(null);

                                List<MagicDto> magicDtos = magicIds.stream()
                                        .filter(magicMap::containsKey)
                                        .map(magicId -> {
                                            Magic magic = magicMap.get(magicId);
                                            List<String> cards = cardsByMagicId.getOrDefault(magicId, List.of()).stream()
                                                    .map(mc -> {
                                                        Card card = cardMap.get(mc.getCardId());
                                                        return card != null ? card.getName().name() : null;
                                                    })
                                                    .filter(Objects::nonNull)
                                                    .collect(Collectors.toList());
                                            return new MagicDto(magicId, magic.getName(), cards);
                                        })
                                        .collect(Collectors.toList());

                                String version = (maxUpdatedAt != null)
                                        ? maxUpdatedAt.format(DateTimeFormatter.ISO_DATE_TIME)
                                        : initialVersion;

                                return new MagicsResponse(version, magicDtos);
                            });
                });
    }
}
