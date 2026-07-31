package com.wordonline.matching.auth.service;

import org.springframework.context.i18n.LocaleContext;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.wordonline.matching.auth.domain.User;
import com.wordonline.matching.auth.domain.UserStatus;
import com.wordonline.matching.auth.dto.UserDetailResponseDto;
import com.wordonline.matching.auth.dto.UserResponseDto;
import com.wordonline.matching.auth.repository.UserRepository;
import com.wordonline.matching.matching.client.AccountClient;
import com.wordonline.matching.global.service.LocalizationService;
import com.wordonline.matching.matching.config.MatchingProperties;
import com.wordonline.matching.matching.domain.MatchTicket;
import com.wordonline.matching.matching.domain.MatchTicketState;
import com.wordonline.matching.matching.repository.MatchTicketRepository;
import com.wordonline.matching.matching.service.MatchNotifier;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AccountClient accountClient;
    private final LocalizationService localizationService;
    private final MatchTicketRepository matchTicketRepository;
    private final MatchNotifier matchNotifier;
    private final MatchingProperties matchingProperties;

    public Mono<UserResponseDto> getUser(long memberId) {
        return findUserDomain(memberId)
                .onErrorResume(e -> initialUser(memberId))
                .map(UserResponseDto::new);
    }

    private Mono<User> initialUser(long memberId) {
        return userRepository.insertUser(memberId)
                .then(userRepository.initUserCard(memberId))
                .then(userRepository.initUserMagic(memberId))
                .then(userRepository.initUserQuest(memberId))
                .then(userRepository.initUserDeck(memberId))
                .then(userRepository.findById(memberId));
    }

    public Mono<UserDetailResponseDto> getUserDetail(Long memberId) {
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

    public Mono<Long> getMmr(Long userId) {
        if (userId == null || userId < 0) return Mono.just(0L);
        return findUserDomain(userId)
                .map(user -> user.getMmr() != null ? user.getMmr() : 0L);
    }

    /**
     * One primary key lookup. No game server calls: whether a session is still alive is the
     * reconciler's job, not something every status poll needs to re-derive.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Mono<UserStatus> getStatus(Long userId) {
        if (userId == null || userId < 0){
            return Mono.empty();
        }

        return matchTicketRepository.findById(userId)
                .map(UserService::toUserStatus)
                .defaultIfEmpty(UserStatus.Online);
    }

    /**
     * Long-poll variant: holds the request until the status changes or {@code waitSeconds} passes.
     * A local notification wakes it immediately; the periodic re-read covers changes made by
     * another lobby instance.
     *
     * Deliberately outside any transaction: a waiting request must not pin a database connection
     * for the whole wait window.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Mono<UserStatus> awaitStatus(Long userId, long waitSeconds) {
        if (waitSeconds <= 0) {
            return getStatus(userId);
        }

        long cappedWait = Math.min(waitSeconds, matchingProperties.getMaxStatusWaitSeconds());
        Duration recheck = Duration.ofMillis(matchingProperties.getStatusRecheckMillis());

        return getStatus(userId).flatMap(initial ->
                Flux.merge(matchNotifier.changesOf(userId), Flux.interval(recheck, recheck))
                        .concatMap(ignored -> getStatus(userId))
                        .filter(status -> status != initial)
                        .next()
                        .timeout(Duration.ofSeconds(cappedWait), Mono.just(initial)));
    }

    private static UserStatus toUserStatus(MatchTicket ticket) {
        // MATCHED still counts as matching: the client should only leave the lobby once a game
        // server has accepted the session.
        return ticket.getState() == MatchTicketState.PLAYING
                ? UserStatus.OnPlaying
                : UserStatus.OnMatching;
    }
}
