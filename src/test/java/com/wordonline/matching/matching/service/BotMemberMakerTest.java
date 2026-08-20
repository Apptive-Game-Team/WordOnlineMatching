package com.wordonline.matching.matching.service;

import com.wordonline.matching.matching.domain.BotPersona;
import com.wordonline.matching.matching.repository.BotPersonaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BotMemberMakerTest {

    @Mock
    private BotPersonaRepository botPersonaRepository;

    private BotMemberMaker botMemberMaker;

    @BeforeEach
    void setUp() {
        botMemberMaker = new BotMemberMaker(botPersonaRepository);
    }

    @Test
    void returnsNameFromEnabledPersona() {
        when(botPersonaRepository.findByUserId(-7L))
                .thenReturn(Mono.just(new BotPersona(-7L, "database bot", true, false)));

        StepVerifier.create(botMemberMaker.getBot(-7L))
                .expectNextMatches(member -> member.getName().equals("database bot"))
                .verifyComplete();
    }

    @Test
    void rejectsMissingPersonaWithoutFallbackName() {
        when(botPersonaRepository.findByUserId(-7L)).thenReturn(Mono.empty());

        StepVerifier.create(botMemberMaker.getBot(-7L))
                .expectErrorMatches(error -> error instanceof IllegalArgumentException
                        && error.getMessage().contains("userId=-7"))
                .verify();
    }

    @Test
    void rejectsDisabledPersona() {
        when(botPersonaRepository.findByUserId(-7L))
                .thenReturn(Mono.just(new BotPersona(-7L, "disabled bot", false, false)));

        StepVerifier.create(botMemberMaker.getBot(-7L))
                .expectErrorMatches(error -> error instanceof IllegalStateException
                        && error.getMessage().contains("disabled"))
                .verify();
    }

    @Test
    void selectsBotFromEnabledPersonaCatalog() {
        when(botPersonaRepository.findRandomEnabled())
                .thenReturn(Mono.just(new BotPersona(-12L, "random bot", true, false)));

        StepVerifier.create(botMemberMaker.getRandomEnabledBotId())
                .expectNext(-12L)
                .verifyComplete();
    }

    @Test
    void selectsTheHospitalityPersonaForTheNovicePath() {
        when(botPersonaRepository.findHospitality())
                .thenReturn(Mono.just(new BotPersona(-3L, "Warm Welcome", true, true)));

        StepVerifier.create(botMemberMaker.getHospitalityBotId())
                .expectNext(-3L)
                .verifyComplete();
    }

    // The caller falls back to the ordinary pool on this error. A missing tutorial opponent must not
    // be the reason a new player cannot start a match.
    @Test
    void reportsWhenNoHospitalityPersonaExists() {
        when(botPersonaRepository.findHospitality()).thenReturn(Mono.empty());

        StepVerifier.create(botMemberMaker.getHospitalityBotId())
                .expectErrorMatches(error -> error instanceof IllegalStateException
                        && error.getMessage().contains("No hospitality bot persona"))
                .verify();
    }

    @Test
    void rejectsSelectionWhenNoPersonaIsEnabled() {
        when(botPersonaRepository.findRandomEnabled()).thenReturn(Mono.empty());

        StepVerifier.create(botMemberMaker.getRandomEnabledBotId())
                .expectErrorMatches(error -> error instanceof IllegalStateException
                        && error.getMessage().contains("No enabled bot persona"))
                .verify();
    }
}
