package com.wordonline.matching.auth.service;

import org.springframework.context.i18n.LocaleContext;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wordonline.matching.auth.domain.User;
import com.wordonline.matching.auth.domain.UserStatus;
import com.wordonline.matching.auth.dto.UserDetailResponseDto;
import com.wordonline.matching.auth.dto.UserResponseDto;
import com.wordonline.matching.auth.repository.UserRepository;
import com.wordonline.matching.deck.service.DeckInitializer;
import com.wordonline.matching.deck.service.DeckService;
import com.wordonline.matching.decoration.service.DecorationInitializer;
import com.wordonline.matching.magic.service.MagicService;
import com.wordonline.matching.matching.client.AccountClient;
import com.wordonline.matching.quest.service.QuestInitializer;
import com.wordonline.matching.service.LocalizationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final DeckService deckService;
    private final DeckInitializer deckInitializer;
    private final QuestInitializer questInitializer;
    private final DecorationInitializer decorationInitializer;
    private final AccountClient accountClient;
    private final LocalizationService localizationService;
    private final MagicService magicService;

    public Mono<User> initialUser(long memberId) {
        return userRepository.insertUser(memberId)
                .then(userRepository.findById(memberId))
                .onErrorResume(e ->
                    Mono.deferContextual(ctx -> {
                        log.error("Error in initial user", e);
                        LocaleContext localeContext = ctx.get(LocaleContext.class);
                        String message = localizationService.getMessage(localeContext, "error.register.failed");
                        return Mono.error(
                                new AuthorizationDeniedException(message)
                        );
                    })
                ).flatMap(saveUser ->
                        deckInitializer.initializeCard(saveUser.getId())
                                .flatMap(deckId -> questInitializer.initializeQuests(saveUser.getId())
                                        .then(decorationInitializer.initialize(saveUser.getId()))
                                        .then(magicService.giveDefaultMagics(saveUser.getId()))
                                        .thenReturn(deckId))
                                .map(deckId -> Tuples.of(saveUser, deckId))
                ).flatMap(tuple -> {
                    tuple.getT1().setSelectedDeckId(tuple.getT2());
                    return userRepository.save(tuple.getT1());
                });
    }

    public Mono<UserResponseDto> getUser(long memberId) {
        return findUserDomain(memberId)
                .onErrorResume(e -> initialUser(memberId))
                .map(UserResponseDto::new);
    }

    public Mono<UserDetailResponseDto> getUserDetail(long memberId) {
        return accountClient.getMember(memberId)
                .map(accountMemberResponseDto -> {
                    log.info(accountMemberResponseDto.toString());
                    return new UserDetailResponseDto(memberId, accountMemberResponseDto);
                });
    }

    public Mono<Void> deleteUser(long userId) {
        return userRepository.deleteById(userId);
    }

    private Mono<User> findUserDomain(long userId) {
        return userRepository.findById(userId)
                .switchIfEmpty(throwAuthorizationDeniedException(userId));
    }

    private Mono<User> throwAuthorizationDeniedException(long userId) {
        return Mono.deferContextual(ctx -> {
            LocaleContext localeContext = ctx.get(LocaleContext.class);
            return Mono.error(
                    new AuthorizationDeniedException(
                            localizationService.getMessage(
                                    localeContext,
                                    "error.user.not.found",
                                    new Object[]{userId})));
        });
    }

    public Mono<Void> markMatching(long userId) {
        if (userId < 0) {
            return Mono.empty();
        }

        return userRepository.updateStatus(userId, UserStatus.OnMatching)
                .then();
    }

    public Mono<Void> markPlaying(long userId) {
        if (userId < 0){
            return Mono.empty();
        }

        return userRepository.updateStatus(userId, UserStatus.OnPlaying)
                .then();
    }

    public Mono<Void> markOnline(long userId) {
        if (userId < 0){
            return Mono.just(0).then();
        }

        return userRepository.updateStatus(userId, UserStatus.Online)
                .then();
    }

    public Mono<UserStatus> getStatus(long userId) {
        if (userId < 0){
            return Mono.empty();
        }

        return findUserDomain(userId)
                .map(User::getStatus);
    }
}
