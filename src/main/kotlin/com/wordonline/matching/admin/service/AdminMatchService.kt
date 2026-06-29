package com.wordonline.matching.admin.service

import com.wordonline.matching.admin.dto.BotMatchRequest
import com.wordonline.matching.matching.dto.MatchedInfoDto
import com.wordonline.matching.matching.service.GameMatchService
import org.springframework.stereotype.Service

@Service
class AdminMatchService(
    private val gameMatchService: GameMatchService,
) {
    suspend fun matchBots(request: BotMatchRequest): MatchedInfoDto {
        val leftBotParticipantId = toBotParticipantId(request.leftBotId)
        val rightBotParticipantId = toBotParticipantId(request.rightBotId)

        require(leftBotParticipantId != rightBotParticipantId) { "leftBotId and rightBotId must be different" }

        return gameMatchService.matchBots(leftBotParticipantId, rightBotParticipantId)
    }

    private fun toBotParticipantId(botId: Long): Long {
        require(botId != 0L) { "botId must not be 0" }
        return -kotlin.math.abs(botId)
    }
}
