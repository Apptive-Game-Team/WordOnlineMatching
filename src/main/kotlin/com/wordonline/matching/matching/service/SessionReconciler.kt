package com.wordonline.matching.matching.service

import com.wordonline.matching.matching.config.MatchingProperties
import com.wordonline.matching.matching.repository.MatchTicketRepository
import com.wordonline.matching.server.service.GameSessionService
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Detects finished games. The game server does not notify the lobby, so someone has to compare
 * live rooms against PLAYING tickets. Doing it here, once every few seconds, is the whole reason
 * the status endpoint no longer has to.
 *
 * ponytail: no leader election, every instance may run this. Clearing a ticket for a user who has
 * no room is idempotent, so duplicate runs are harmless. Add a lock only if the room list fan-out
 * itself becomes expensive.
 */
@Component
class SessionReconciler(
    private val gameSessionService: GameSessionService,
    private val matchTicketRepository: MatchTicketRepository,
    private val matchNotifier: MatchNotifier,
    private val properties: MatchingProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${matching.reconcile-interval-ms:10000}")
    fun reconcile() {
        try {
            runBlocking { runReconcile() }
        } catch (e: Exception) {
            // Includes "a game server did not answer". Skipping a round is correct here: an
            // incomplete room list would look like every session had ended.
            log.error("Session reconcile skipped", e)
        }
    }

    private suspend fun runReconcile() {
        val candidates = matchTicketRepository
            .findPlayingOlderThan(properties.reconcileGraceSeconds)
            .collectList()
            .awaitSingle()
        if (candidates.isEmpty()) return

        val rooms = gameSessionService.getAllGameSessions().awaitSingle()
        val playingUserIds = rooms.rooms()
            .flatMap { listOfNotNull(it.leftUserId(), it.rightUserId()) }
            .toSet()

        val finished = candidates.filterNot { playingUserIds.contains(it) }
        if (finished.isEmpty()) return

        matchTicketRepository.deleteAllById(finished).awaitSingleOrNull()
        finished.forEach(matchNotifier::changed)
        log.info("Cleared {} finished match tickets", finished.size)
    }
}
