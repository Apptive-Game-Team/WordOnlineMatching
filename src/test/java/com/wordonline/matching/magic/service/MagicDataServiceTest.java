package com.wordonline.matching.magic.service;

import com.wordonline.matching.deck.domain.Card;
import com.wordonline.matching.deck.dto.CardType;
import com.wordonline.matching.deck.repository.CardRepository;
import com.wordonline.matching.magic.domain.Magic;
import com.wordonline.matching.magic.domain.MagicCard;
import com.wordonline.matching.magic.dto.MagicsResponse;
import com.wordonline.matching.magic.repository.MagicCardRepository;
import com.wordonline.matching.magic.repository.MagicRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MagicDataServiceTest {

    @Mock
    private MagicCardRepository magicCardRepository;

    @Mock
    private MagicRepository magicRepository;

    @Mock
    private CardRepository cardRepository;

    @InjectMocks
    private MagicDataService magicDataService;

    @Test
    @DisplayName("버전_조회시_변경이_있으면_전체_마법_정보를_반환")
    void getMagics_WithVersion_ReturnsAllMagicsWhenAnyChangeExists() {
        String currentVersion = "2024-01-01T00:00:00";
        LocalDateTime changedAt = LocalDateTime.parse("2024-01-02T12:00:00");

        MagicCard changedMagicCard = new MagicCard(2L, 20L, 200L, changedAt);

        MagicCard fullFireballCardOne = new MagicCard(1L, 10L, 100L, LocalDateTime.parse("2024-01-01T00:00:00"));
        MagicCard fullFireballCardTwo = new MagicCard(2L, 10L, 101L, LocalDateTime.parse("2024-01-01T00:00:00"));
        MagicCard fullIceWallCard = new MagicCard(3L, 20L, 200L, changedAt);
        Card fireCard = mockCard(100L, CardType.Fire);
        Card shootCard = mockCard(101L, CardType.Shoot);
        Card waterCard = mockCard(200L, CardType.Water);

        when(magicCardRepository.findAllByUpdatedMagicsSince(any()))
                .thenReturn(Flux.just(changedMagicCard));
        when(magicCardRepository.findAll())
                .thenReturn(Flux.just(fullFireballCardOne, fullFireballCardTwo, fullIceWallCard));
        when(magicRepository.findAllById(List.of(10L, 20L)))
                .thenReturn(Flux.just(
                        new Magic(10L, "fireball"),
                        new Magic(20L, "ice_wall")
                ));
        when(cardRepository.findAllById(List.of(100L, 101L, 200L)))
                .thenReturn(Flux.just(fireCard, shootCard, waterCard));

        StepVerifier.create(magicDataService.getMagics(currentVersion))
                .assertNext(response -> {
                    assertFullMagicSnapshot(response);
                    assert response.version().equals("2024-01-02T12:00:00");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("버전_조회시_변경이_없으면_빈_응답과_기존_버전을_반환")
    void getMagics_WithVersion_ReturnsEmptyWhenNothingChanged() {
        String currentVersion = "2024-01-01T00:00:00";

        when(magicCardRepository.findAllByUpdatedMagicsSince(any()))
                .thenReturn(Flux.empty());

        StepVerifier.create(magicDataService.getMagics(currentVersion))
                .assertNext(response -> {
                    assert response.magics().isEmpty();
                    assert response.version().equals(currentVersion);
                })
                .verifyComplete();
    }

    private Card mockCard(Long id, CardType name) {
        Card card = mock(Card.class);
        when(card.getId()).thenReturn(id);
        when(card.getName()).thenReturn(name);
        return card;
    }

    private void assertFullMagicSnapshot(MagicsResponse response) {
        assert response.magics().size() == 2;
        assert response.magics().stream().anyMatch(magic ->
                magic.id().equals(10L)
                        && magic.name().equals("fireball")
                        && magic.cards().equals(List.of("Fire", "Shoot")));
        assert response.magics().stream().anyMatch(magic ->
                magic.id().equals(20L)
                        && magic.name().equals("ice_wall")
                        && magic.cards().equals(List.of("Water")));
    }
}
