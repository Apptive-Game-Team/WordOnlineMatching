package com.wordonline.matching.auth.service;

import com.wordonline.matching.auth.domain.User;
import com.wordonline.matching.auth.repository.UserRepository;
import com.wordonline.matching.deck.service.DeckInitializer;
import com.wordonline.matching.matching.client.AccountClient;
import com.wordonline.matching.quest.service.QuestInitializer;
import com.wordonline.matching.service.LocalizationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private DeckInitializer deckInitializer;
    @Mock
    private QuestInitializer questInitializer;
    @Mock
    private AccountClient accountClient;
    @Mock
    private LocalizationService localizationService;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("유저_초기화시_퀘스트도_초기화_성공")
    void initialUser_initializesQuests_Success() {
        long userId = 1L;
        long deckId = 10L;
        User user = new User(userId, com.wordonline.matching.auth.domain.UserStatus.Online, null, 0);

        when(userRepository.insertUser(userId)).thenReturn(Mono.empty());
        when(userRepository.findById(userId)).thenReturn(Mono.just(user));
        when(deckInitializer.initializeCard(userId)).thenReturn(Mono.just(deckId));
        when(questInitializer.initializeQuests(userId)).thenReturn(Mono.empty());
        when(userRepository.save(any(User.class))).thenReturn(Mono.just(user));

        Mono<User> result = userService.initialUser(userId);

        StepVerifier.create(result)
                .expectNextMatches(savedUser -> savedUser.getId() == userId && savedUser.getSelectedDeckId() == deckId)
                .verifyComplete();

        verify(deckInitializer).initializeCard(userId);
        verify(questInitializer).initializeQuests(userId);
    }
}
