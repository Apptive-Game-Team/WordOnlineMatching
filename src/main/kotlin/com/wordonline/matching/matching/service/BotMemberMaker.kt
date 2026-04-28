package com.wordonline.matching.matching.service

import com.wordonline.matching.matching.dto.AccountMemberResponseDto
import org.springframework.stereotype.Component
import kotlin.random.Random

@Component
class BotMemberMaker {

    val randomBotMemberId: Long
        get() = -1L * (Random.nextInt(2) + 1)

    suspend fun getBot(botId: Long): AccountMemberResponseDto {
        val name = when (botId.toInt()) {
            -1 -> "master of everything"
            -2 -> "master of lightning water"
            -3 -> "master of fire rock"
            -4 -> "master of water nature"
            else -> "bot"
        }
        return AccountMemberResponseDto("bot@team6515.com", name)
    }
}
