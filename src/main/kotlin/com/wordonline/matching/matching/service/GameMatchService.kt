package com.wordonline.matching.matching.service

import com.wordonline.matching.auth.service.UserService
import com.wordonline.matching.deck.service.DeckService
import com.wordonline.matching.matching.dto.MatchedInfoDto
import com.wordonline.matching.matching.dto.SessionDto
import com.wordonline.matching.matching.dto.SimpleMessageDto
import com.wordonline.matching.matching.repository.MatchingQueueRepository
import com.wordonline.matching.session.service.LegacyGameMatchService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class GameMatchService(
    private val botMemberMaker: BotMemberMaker,
    private val legacyGameMatchService: LegacyGameMatchService,
    private val userService: UserService,
    private val deckService: DeckService,
    private val matchingQueueRepository: MatchingQueueRepository,
) {
    private val scope = CoroutineScope(Dispatchers.Default)

    suspend fun matchPractice(userId: Long): MatchedInfoDto {
        val sessionId = "bot-${matchingQueueRepository.nextSessionId().awaitSingle()}"
        val sessionDto = SessionDto.Practice(sessionId, userId, botMemberMaker.randomBotMemberId)
        return legacyGameMatchService.createSession(sessionDto).awaitSingle()
    }

    suspend fun matchPVE(userId: Long, scenarioId: Long): MatchedInfoDto {
        val sessionId = "pve-${matchingQueueRepository.nextSessionId().awaitSingle()}"
        val sessionDto = SessionDto.PVE(sessionId, userId, scenarioId)
        return legacyGameMatchService.createSession(sessionDto).awaitSingle()
    }

    suspend fun match(userId: Long): SimpleMessageDto {
        if (!enqueue(userId)) {
            return SimpleMessageDto("Failed to enqueue user")
        }

        return SimpleMessageDto("Successfully Enqueued")
    }

    private suspend fun enqueue(userId: Long): Boolean {
        if (matchingQueueRepository.isInQueue(userId).awaitSingle()) {
            return true
        }

        return try {
            userService.markMatching(userId).awaitSingleOrNull()
            val hasDeck = deckService.hasSelectedDeck(userId).awaitSingle()
            if (!hasDeck) {
                throw IllegalStateException("Deck has not been selected")
            }
            matchingQueueRepository.enqueue(userId).awaitSingle()
            true
        } catch (e: Exception) {
            userService.markOnline(userId).awaitSingleOrNull()
            false
        }
    }

    suspend fun removeFromQueue(userId: Long) {
        matchingQueueRepository.remove(userId).awaitSingleOrNull()
    }

    @Scheduled(fixedRate = 5000)
    fun tryMatching() {
        scope.launch {
            val pair = matchingQueueRepository.dequeuePair().awaitSingle()
            if (pair.size < 2) return@launch

            val uid1 = pair[0]
            val uid2 = pair[1]
            val sessionId = "session-${matchingQueueRepository.nextSessionId().awaitSingle()}"
            val sessionDto = SessionDto.from(sessionId, uid1, uid2)

            try {
                legacyGameMatchService.createSession(sessionDto).awaitSingle()
                userService.markPlaying(uid1).awaitSingleOrNull()
                userService.markPlaying(uid2).awaitSingleOrNull()
            } catch (e: Exception) {
                userService.markOnline(uid1).awaitSingleOrNull()
                userService.markOnline(uid2).awaitSingleOrNull()
            }
        }
    }

    suspend fun getQueueLength(): Long = matchingQueueRepository.size().awaitSingle()

    suspend fun isInQueue(userId: Long): Boolean = matchingQueueRepository.isInQueue(userId).awaitSingle()
}
