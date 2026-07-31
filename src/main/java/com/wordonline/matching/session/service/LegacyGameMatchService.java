package com.wordonline.matching.session.service;

import java.util.Optional;

import org.springframework.context.i18n.LocaleContext;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.wordonline.matching.auth.dto.UserDetailResponseDto;
import com.wordonline.matching.auth.service.UserService;
import com.wordonline.matching.matching.domain.MatchTicket;
import com.wordonline.matching.matching.domain.MatchTicketState;
import com.wordonline.matching.matching.dto.MatchedInfoDto;
import com.wordonline.matching.matching.dto.SessionDto;
import com.wordonline.matching.matching.repository.MatchTicketRepository;
import com.wordonline.matching.server.entity.Server;
import com.wordonline.matching.server.service.GameServerManagementService;
import com.wordonline.matching.global.service.LocalizationService;
import com.wordonline.matching.session.dto.SimpleBooleanDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

@Slf4j
@Service
@RequiredArgsConstructor
public class LegacyGameMatchService {

    private final WebClient.Builder webClientBuilder;
    private final MatchTicketRepository matchTicketRepository;
    private final LocalizationService localizationService;
    private final UserService userService;
    private final GameServerManagementService gameServerManagementService;

    public Mono<MatchedInfoDto> createSession(SessionDto sessionDto) {
        Optional<Server> optionalServer = gameServerManagementService.getAvailableServer();
        return getWebClient(optionalServer).flatMap(webClient ->
                getUserDetails(sessionDto.getUid1(), sessionDto.getUid2()).flatMap(tuple ->
                        webClient.post().uri("/api/server/game-sessions")
                                .body(Mono.just(sessionDto), SessionDto.class)
                                .accept(MediaType.APPLICATION_JSON)
                                .retrieve()
                                .bodyToMono(SimpleBooleanDto.class)
                                .map(SimpleBooleanDto::value)
                                .doOnNext(isSuccess -> log.info("Response from game server: {}", isSuccess))
                                .flatMap(isSuccess -> {
                                    if (!isSuccess) return getException();
                                    log.info("Session Created");
                                    String serverUrl = optionalServer.get().getUrl();
                                    MatchedInfoDto matchedInfoDto = new MatchedInfoDto(
                                            "Successfully Matched",
                                            serverUrl,
                                            tuple.getT1(),
                                            tuple.getT2(),
                                            sessionDto.getSessionId()
                                    );
                                    return storeTickets(sessionDto, serverUrl)
                                            .thenReturn(matchedInfoDto);
                                })));
    }

    /**
     * The ticket doubles as the session recovery record, so practice and PVE sessions get
     * reconnect support the same way queued matches do.
     */
    private Mono<Void> storeTickets(SessionDto sessionDto, String serverUrl) {
        return Mono.when(
                storeTicket(sessionDto.getUid1(), sessionDto, serverUrl),
                storeTicket(sessionDto.getUid2(), sessionDto, serverUrl)
        );
    }

    private Mono<Void> storeTicket(Long userId, SessionDto sessionDto, String serverUrl) {
        if (userId == null || userId < 0) return Mono.empty();

        Long rightUserId = sessionDto.getUid2();
        if (rightUserId == null) {
            return matchTicketRepository.markPlayingSolo(
                    userId, sessionDto.getSessionId(), serverUrl, sessionDto.getUid1()).then();
        }
        return matchTicketRepository.markPlaying(
                userId, sessionDto.getSessionId(), serverUrl, sessionDto.getUid1(), rightUserId).then();
    }

    public Mono<MatchedInfoDto> getMatchInfo(long userId) {
        return matchTicketRepository.findById(userId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Session Not Found")))
                .flatMap(this::mapToMatchedInfo);
    }

    private Mono<MatchedInfoDto> mapToMatchedInfo(MatchTicket ticket) {
        if (ticket.getState() != MatchTicketState.PLAYING || ticket.getSessionId() == null) {
            return Mono.error(new IllegalArgumentException("Session Not Found"));
        }

        return getUserDetails(ticket.getLeftUserId(), ticket.getRightUserId())
                .map(tuple -> new MatchedInfoDto(
                        "Successfully Match Info Recovered",
                        ticket.getServerUrl(),
                        tuple.getT1(),
                        tuple.getT2(),
                        ticket.getSessionId()
                ));
    }

    private Mono<Tuple2<UserDetailResponseDto, UserDetailResponseDto>> getUserDetails(long userId1, Long userId2) {
        return Mono.zip(
                userService.getUserDetail(userId1),
                userId2 != null ? userService.getUserDetail(userId2)
                        : Mono.just(new UserDetailResponseDto(0L, "dummy", "dummy@team6515.com"))
        );
    }

    private <T> Mono<T> getException() {
        return Mono.deferContextual(ctx -> {
            LocaleContext localeContext = ctx.get(LocaleContext.class);
            return Mono.error(
                    new IllegalArgumentException(localizationService.getMessage(localeContext, "error.member.not.found")));
        });
    }

    private Mono<WebClient> getWebClient(Optional<Server> server) {
        return Mono.justOrEmpty(server)
                .switchIfEmpty(Mono.error(new IllegalStateException("No available server found")))
                .map(notNullServer -> webClientBuilder.baseUrl(notNullServer.getUrl()).build());
    }
}
