package com.wordonline.matching.matching.service

import com.wordonline.matching.auth.service.UserService
import com.wordonline.matching.deck.service.DeckService
import com.wordonline.matching.matching.dto.MatchedInfoDto
import com.wordonline.matching.matching.dto.SessionDto
import com.wordonline.matching.matching.dto.SimpleMessageDto
import com.wordonline.matching.session.service.LegacyGameMatchService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.channels.awaitClose

@Service
class GameMatchService(
    private val botMemberMaker: BotMemberMaker,
    private val legacyGameMatchService: LegacyGameMatchService,
    private val serverEventService: ServerEventService,
    private val userService: UserService,
    private val deckService: DeckService,
) {
    private val sessionIdCounter = AtomicInteger(1)
    private val matchingQueue = ConcurrentLinkedQueue<Long>()
    private val scope = CoroutineScope(Dispatchers.Default)

    suspend fun matchPractice(userId: Long): MatchedInfoDto {
        val sessionDto = SessionDto.Practice(
            "bot-" + sessionIdCounter.incrementAndGet(),
            userId,
            botMemberMaker.randomBotMemberId
        )
        return legacyGameMatchService.createSession(sessionDto).awaitSingle()
    }

    suspend fun matchPVE(userId: Long, scenarioId: Long): MatchedInfoDto {
        val sessionDto = SessionDto.PVE("pve-" + sessionIdCounter.incrementAndGet(), userId, scenarioId)
        return legacyGameMatchService.createSession(sessionDto).awaitSingle()
    }

    fun match(userId: Long): Flow<Any> = callbackFlow {
        if (!enqueue(userId)) {
            trySend(SimpleMessageDto("Failed to enqueue user"))
            close()
            return@callbackFlow
        }

        trySend(SimpleMessageDto("Successfully Enqueued"))

        val subscription = serverEventService.subscribe(userId) { finalUserId ->
            scope.launch {
                removeFromQueue(finalUserId)
                userService.markOnline(finalUserId).awaitSingleOrNull()
            }
        }.subscribe { event ->
            trySend(event)
        }

        awaitClose {
            subscription.dispose()
        }
    }

    private suspend fun enqueue(userId: Long): Boolean {
        if (matchingQueue.contains(userId)) {
            return true
        }

        return try {
            userService.markMatching(userId).awaitSingleOrNull()
            val hasDeck = deckService.hasSelectedDeck(userId).awaitSingle()
            if (!hasDeck) {
                throw IllegalStateException("Deck has not been selected")
            }
            if (!matchingQueue.contains(userId)) {
                matchingQueue.add(userId)
            }
            true
        } catch (e: Exception) {
            userService.markOnline(userId).awaitSingleOrNull()
            false
        }
    }

    suspend fun removeFromQueue(userId: Long) {
        matchingQueue.remove(userId)
        serverEventService.unsubscribe(userId).awaitSingleOrNull()
    }

    @Scheduled(fixedRate = 1000)
    fun tryMatching() {
        if (matchingQueue.size < 2) {
            return
        }

        val uid1 = matchingQueue.poll()
        val uid2 = matchingQueue.poll()

        if (uid1 == null || uid2 == null) {
            if (uid1 != null) matchingQueue.add(uid1)
            return
        }

        scope.launch {
            val sessionId = "session-" + sessionIdCounter.incrementAndGet()
            val sessionDto = SessionDto.from(sessionId, uid1, uid2)

            try {
                val matchedInfoDto = legacyGameMatchService.createSession(sessionDto).awaitSingle()
                userService.markPlaying(uid1).awaitSingleOrNull()
                userService.markPlaying(uid2).awaitSingleOrNull()

                serverEventService.send(uid1, matchedInfoDto).awaitSingleOrNull()
                serverEventService.send(uid2, matchedInfoDto).awaitSingleOrNull()
            } catch (e: Exception) {
                userService.markOnline(uid1).awaitSingleOrNull()
                userService.markOnline(uid2).awaitSingleOrNull()
            }
        }
    }

    fun getQueueLength(): Int {
        return matchingQueue.size
    }

    fun isInQueue(userId: Long): Boolean {
        return matchingQueue.contains(userId)
    }
}