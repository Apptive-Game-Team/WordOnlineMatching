package com.wordonline.matching.matching.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.wordonline.matching.matching.dto.AccountMemberResponseDto;
import com.wordonline.matching.matching.service.BotMemberMaker;
import com.wordonline.matching.global.service.LocalizationService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class AccountClient {

    private final WebClient webClient;
    private final LocalizationService localizationService;
    private final BotMemberMaker botMemberMaker;

    public AccountClient(WebClient.Builder builder,
                        @Value("${team6515.server.account.url}") String accountServerUrl,
                        LocalizationService localizationService, BotMemberMaker botMemberMaker) {
        this.webClient = builder.baseUrl(accountServerUrl)
                .build();
        this.localizationService = localizationService;
        this.botMemberMaker = botMemberMaker;
    }

    public Mono<AccountMemberResponseDto> getMember(long memberId) {
        if (memberId <= 0) {
            return botMemberMaker.getBot(memberId);
        }

        return webClient.get().uri("/api/members/" + memberId)
             .retrieve()
             .onStatus(httpStatusCode -> {
                 log.info("[Account Client] Status Code : " + httpStatusCode);
                 return httpStatusCode != HttpStatus.OK;
             }, clientResponse -> getException())
             .bodyToMono(AccountMemberResponseDto.class);
    }

    private Mono<Throwable> getException() {
        return Mono.deferContextual(ctx -> {
            LocaleContext localeContext = ctx.get(LocaleContext.class);
            return Mono.error(
                    new IllegalArgumentException(localizationService.getMessage(localeContext, "error.member.not.found")));
        });
    }
}
