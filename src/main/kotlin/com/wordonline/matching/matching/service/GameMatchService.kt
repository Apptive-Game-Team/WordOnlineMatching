package com.wordonline.matching.matching.service

import com.wordonline.matching.auth.service.UserService
import com.wordonline.matching.deck.service.DeckService
import com.wordonline.matching.matching.config.MatchingProperties
import com.wordonline.matching.matching.domain.MatchTicketState
import com.wordonline.matching.matching.dto.MatchedInfoDto
import com.wordonline.matching.matching.dto.SessionDto
import com.wordonline.matching.matching.dto.SimpleMessageDto
import com.wordonline.matching.matching.repository.MatchTicketRepository
import com.wordonline.matching.session.service.LegacyGameMatchService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class GameMatchService(
    private val botMemberMaker: BotMemberMaker,
    private val legacyGameMatchService: LegacyGameMatchService,
    private val userService: UserService,
    private val deckService: DeckService,
    private val matchTicketRepository: MatchTicketRepository,
    private val matchPairClaimer: MatchPairClaimer,
    private val matchNotifier: MatchNotifier,
    private val properties: MatchingProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun matchPractice(userId: Long): MatchedInfoDto {
        val sessionId = "bot-${matchTicketRepository.nextSessionId().awaitSingle()}"
        val botId = botMemberMaker.getRandomEnabledBotId().awaitSingle()
        val sessionDto = SessionDto.Practice(sessionId, userId, botId)
        return legacyGameMatchService.createSession(sessionDto).awaitSingle()
    }

    suspend fun matchBots(leftBotId: Long, rightBotId: Long): MatchedInfoDto {
        val sessionId = "admin-bot-${matchTicketRepository.nextSessionId().awaitSingle()}"
        val sessionDto = SessionDto.Practice(sessionId, leftBotId, rightBotId)
        return legacyGameMatchService.createSession(sessionDto).awaitSingle()
    }

    suspend fun matchPVE(userId: Long, scenarioId: Long): MatchedInfoDto {
        val sessionId = "pve-${matchTicketRepository.nextSessionId().awaitSingle()}"
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
            validateSelectedDeck(userId)
            val mmr = userService.getMmr(userId).awaitSingle()
            matchTicketRepository.enqueue(userId, mmr).awaitSingleOrNull()
            matchNotifier.changed(userId)
            log.info("User enqueued for matching: userId={}, mmr={}", userId, mmr)
            true
        } catch (e: Exception) {
            log.warn("Failed to enqueue user for matching: userId={}", userId, e)
            matchTicketRepository.deleteQueued(userId).awaitSingleOrNull()
            false
        }
    }

    private suspend fun validateSelectedDeck(userId: Long) {
        val hasValidDeck = deckService.hasValidSelectedDeck(userId).awaitSingle()
        if (!hasValidDeck) throw IllegalStateException("Deck is invalid or has not been selected")
    }

    suspend fun removeFromQueue(userId: Long) {
        matchTicketRepository.deleteQueued(userId).awaitSingleOrNull()
        matchNotifier.changed(userId)
    }

    /**
     * Blocking on purpose. The scheduler thread is not an event loop thread, and blocking it is
     * what makes `fixedDelay` mean "ticks never overlap". The previous version launched each tick
     * on its own scope, so slow game servers let ticks pile up on top of each other.
     */
    @Scheduled(fixedDelayString = "\${matching.tick-interval-ms:1000}")
    fun tryMatching() {
        try {
            runBlocking { runTick() }
        } catch (e: Exception) {
            log.error("Matching tick failed", e)
        }
    }

    private suspend fun runTick() {
        matchTicketRepository.deleteExpiredQueued(properties.queueTimeoutSeconds).awaitSingleOrNull()
        matchTicketRepository.recoverStuckMatched(properties.stuckMatchedSeconds).awaitSingleOrNull()

        val pairs = matchPairClaimer.claimPairs().awaitSingle()
        if (pairs.isEmpty()) return

        coroutineScope {
            pairs.map { pair -> async { createSessionFor(pair) } }.awaitAll()
        }
    }

    private suspend fun createSessionFor(pair: ClaimedPair) {
        val sessionDto = SessionDto.from(pair.sessionId, pair.leftUserId, pair.rightUserId)
        try {
            legacyGameMatchService.createSession(sessionDto).awaitSingle()
            log.info(
                "Users matched: left={}, right={}, sessionId={}",
                pair.leftUserId, pair.rightUserId, pair.sessionId
            )
        } catch (e: Exception) {
            log.error(
                "Failed to create matched session: left={}, right={}, sessionId={}",
                pair.leftUserId, pair.rightUserId, pair.sessionId, e
            )
            requeueOrDrop(pair.leftUserId)
            requeueOrDrop(pair.rightUserId)
        } finally {
            matchNotifier.changed(pair.leftUserId)
            matchNotifier.changed(pair.rightUserId)
        }
    }

    private suspend fun requeueOrDrop(userId: Long) {
        val requeued = (matchTicketRepository.requeue(userId, properties.maxRetry).awaitSingleOrNull() ?: 0L) > 0
        if (requeued) return

        matchTicketRepository.deleteById(userId).awaitSingleOrNull()
        log.warn("Match ticket dropped after exhausting session creation retries: userId={}", userId)
    }

    suspend fun getQueueLength(): Long = matchTicketRepository.countQueued().awaitSingle()

    suspend fun isInQueue(userId: Long): Boolean =
        matchTicketRepository.findById(userId)
            .map { it.state == MatchTicketState.QUEUED }
            .defaultIfEmpty(false)
            .awaitSingle()
}
