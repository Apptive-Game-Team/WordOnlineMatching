package com.wordonline.matching.matching.service

import com.wordonline.matching.matching.dto.AccountMemberResponseDto
import com.wordonline.matching.matching.repository.BotPersonaRepository
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class BotMemberMaker(
    private val botPersonaRepository: BotPersonaRepository,
) {

    fun getRandomEnabledBotId(): Mono<Long> = botPersonaRepository.findRandomEnabledUserId()
        .switchIfEmpty(Mono.error(IllegalStateException("No enabled bot persona is available.")))

    fun getBot(botId: Long): Mono<AccountMemberResponseDto> {
        require(botId < 0) { "Bot user ID must be negative." }

        return botPersonaRepository.findByUserId(botId)
            .switchIfEmpty(Mono.error(IllegalArgumentException("Bot persona not found: userId=$botId")))
            .flatMap { persona ->
                if (!persona.enabled) {
                    Mono.error(IllegalStateException("Bot persona is disabled: userId=$botId"))
                } else {
                    Mono.just(AccountMemberResponseDto("bot@team6515.com", persona.name))
                }
            }
    }
}
