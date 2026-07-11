package com.wordonline.matching.matching.service

import com.wordonline.matching.auth.service.UserService
import com.wordonline.matching.deck.service.DeckService
import com.wordonline.matching.matching.dto.MatchedInfoDto
import com.wordonline.matching.matching.dto.SessionDto
import com.wordonline.matching.matching.dto.SimpleMessageDto
import com.wordonline.matching.matching.repository.MatchingQueueRepository
import com.wordonline.matching.session.service.LegacyGameMatchService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.reactive.collect
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class GameMatchService(
    private val botMemberMaker: BotMemberMaker,
    private val legacyGameMatchService: LegacyGameMatchService,
    private val userService: UserService,
    private val deckService: DeckService,
    private val matchingQueueRepository: MatchingQueueRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        log.error("Unexpected error while trying to match users", throwable)
    }
    private val scope = CoroutineScope(Dispatchers.Default + exceptionHandler)

    suspend fun matchPractice(userId: Long): MatchedInfoDto {
        val sessionId = "bot-${matchingQueueRepository.nextSessionId().awaitSingle()}"
        val botId = botMemberMaker.getRandomEnabledBotId().awaitSingle()
        val sessionDto = SessionDto.Practice(sessionId, userId, botId)
        return legacyGameMatchService.createSession(sessionDto).awaitSingle()
    }

    suspend fun matchBots(leftBotId: Long, rightBotId: Long): MatchedInfoDto {
        val sessionId = "admin-bot-${matchingQueueRepository.nextSessionId().awaitSingle()}"
        val sessionDto = SessionDto.Practice(sessionId, leftBotId, rightBotId)
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
        return try {
            if (matchingQueueRepository.isInQueue(userId).awaitSingle()) {
                validateSelectedDeck(userId)
                val mmr = userService.getMmr(userId).awaitSingle()
                matchingQueueRepository.enqueue(userId, mmr).awaitSingleOrNull()
                log.info("Matching queue entry refreshed: userId={}, mmr={}", userId, mmr)
                return true
            }

            userService.markMatching(userId).awaitSingleOrNull()
            validateSelectedDeck(userId)
            val mmr = userService.getMmr(userId).awaitSingle()
            matchingQueueRepository.enqueue(userId, mmr).awaitSingleOrNull()
            log.info("User enqueued for matching: userId={}, mmr={}", userId, mmr)
            true
        } catch (e: Exception) {
            log.warn("Failed to enqueue user for matching: userId={}", userId, e)
            matchingQueueRepository.remove(userId).awaitSingleOrNull()
            userService.markOnline(userId).awaitSingleOrNull()
            false
        }
    }

    private suspend fun validateSelectedDeck(userId: Long) {
        val hasValidDeck = deckService.hasValidSelectedDeck(userId).awaitSingle()
        if (!hasValidDeck) throw IllegalStateException("Deck is invalid or has not been selected")
    }

    suspend fun removeFromQueue(userId: Long) {
        matchingQueueRepository.remove(userId).awaitSingleOrNull()
        userService.markOnline(userId).awaitSingleOrNull()
    }

    @Scheduled(fixedRate = 5000)
    fun tryMatching() {
        scope.launch {
            matchingQueueRepository.removeExpired().collect { userId ->
                if (!matchingQueueRepository.isInQueue(userId).awaitSingle()) {
                    log.info("Expired matching queue entry removed: userId={}", userId)
                    userService.markOnline(userId).awaitSingleOrNull()
                }
            }

            val pair = matchingQueueRepository.dequeueBestPair().awaitSingle()
            if (pair.size < 2) return@launch

            val uid1 = pair[0]
            val uid2 = pair[1]
            val sessionId = "session-${matchingQueueRepository.nextSessionId().awaitSingle()}"
            val sessionDto = SessionDto.from(sessionId, uid1, uid2)

            try {
                legacyGameMatchService.createSession(sessionDto).awaitSingle()
                log.info("Users matched: uid1={}, uid2={}, sessionId={}", uid1, uid2, sessionId)
                userService.markPlaying(uid1).awaitSingleOrNull()
                userService.markPlaying(uid2).awaitSingleOrNull()
            } catch (e: Exception) {
                log.error("Failed to create matched session: uid1={}, uid2={}, sessionId={}", uid1, uid2, sessionId, e)
                userService.markOnline(uid1).awaitSingleOrNull()
                userService.markOnline(uid2).awaitSingleOrNull()
            } finally {
                matchingQueueRepository.remove(uid1).awaitSingleOrNull()
                matchingQueueRepository.remove(uid2).awaitSingleOrNull()
            }
        }
    }

    suspend fun getQueueLength(): Long = matchingQueueRepository.size().awaitSingle()

    suspend fun isInQueue(userId: Long): Boolean = matchingQueueRepository.isInQueue(userId).awaitSingle()
}
