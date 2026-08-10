package com.wordonline.matching.matching.service

import com.wordonline.matching.matching.config.MatchTicketProperties
import com.wordonline.matching.matching.domain.MatchTicket
import com.wordonline.matching.matching.domain.SessionLiveness
import com.wordonline.matching.matching.repository.MatchTicketRepository
import com.wordonline.matching.server.service.ServerHealthRegistry
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

/**
 * Safety net for `MATCHED` tickets nobody reports.
 *
 * A client report clears the common case, but when both players quit the game there is
 * nobody left to tell the lobby that the session died with its host, and the ticket would
 * block re-queueing forever.
 *
 * The sweep confirms a loss only on evidence:
 *
 * - [SessionLiveness.LOST] - the host answered "no such session", or its boot generation
 *   changed. Conclusive on its own.
 * - [SessionLiveness.UNKNOWN] - the host said nothing. Confirmed only once
 *   [ServerHealthRegistry] has already taken that server out of rotation, which takes
 *   `gameserver.failure-threshold` consecutive failed probes. That reuse is deliberate: a
 *   single missed probe must never end a live match, and duplicating a second failure
 *   counter here would just be a second thing to tune wrong.
 */
@Service
class MatchedTicketReconciler(
    private val matchTicketRepository: MatchTicketRepository,
    private val sessionLivenessProbe: SessionLivenessProbe,
    private val lostSessionRecovery: LostSessionRecovery,
    private val serverHealthRegistry: ServerHealthRegistry,
    private val properties: MatchTicketProperties,
) {
    private val clock = Clock.systemUTC()
    private val log = LoggerFactory.getLogger(javaClass)
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        log.error("Unexpected error while reconciling matched tickets", throwable)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)

    /** `@Scheduled` cannot invoke a suspend function, so the annotated method only launches. */
    @Scheduled(fixedDelayString = "\${matching.ticket.matched-scan-interval}")
    fun scan() {
        scope.launch { reconcile() }
    }

    /**
     * Fast lane for tickets a client already reported and the lobby could not verify.
     *
     * No grace period here: the ticket was reported, and the same verdict rules still decide
     * the outcome. Only the waiting time changes.
     */
    @Scheduled(fixedDelayString = "\${matching.ticket.pending-scan-interval}")
    fun drainPendingVerification() {
        scope.launch { reconcilePending() }
    }

    suspend fun reconcile() {
        val settledBefore = Instant.now(clock).minus(properties.matchedScanGrace)
        audit(matchTicketRepository.settledMatched(settledBefore, properties.matchedScanBatchSize))
    }

    suspend fun reconcilePending() {
        val tickets = matchTicketRepository.pendingVerification(properties.matchedScanBatchSize)
        if (tickets.isEmpty()) return
        for (ticket in audit(tickets)) {
            matchTicketRepository.clearPendingVerification(ticket.ticketId)
        }
    }

    /** Returns the tickets that reached a verdict, whether the session was alive or gone. */
    private suspend fun audit(tickets: List<MatchTicket>): List<MatchTicket> {
        val handledSessions = mutableSetOf<String>()
        val decided = mutableListOf<MatchTicket>()

        for (ticket in tickets) {
            val sessionId = ticket.matchInfo?.sessionId ?: continue
            // Both tickets of a pair sit next to each other in the index and are retired
            // together, so the second one would only re-probe a host we already asked.
            if (!handledSessions.add(sessionId)) continue

            when (sessionLivenessProbe.check(ticket)) {
                SessionLiveness.ALIVE -> decided += ticket
                SessionLiveness.LOST -> decided += release(ticket, sessionId)
                SessionLiveness.UNKNOWN -> if (isHostOutOfRotation(ticket)) decided += release(ticket, sessionId)
            }
        }
        return decided
    }

    private suspend fun release(ticket: MatchTicket, sessionId: String): MatchTicket {
        lostSessionRecovery.release(ticket)?.also {
            log.warn(
                "Reconciled lost session: ticketId={}, sessionId={}, userId={}",
                ticket.ticketId,
                sessionId,
                ticket.userId,
            )
        }
        return ticket
    }

    /**
     * A host is only "gone" once the health registry says so. An unresolvable server row
     * stays inconclusive: without health history there is no hysteresis to lean on, and
     * guessing would risk ending a live session.
     */
    private fun isHostOutOfRotation(ticket: MatchTicket): Boolean {
        val serverId = sessionLivenessProbe.hostOf(ticket)?.id ?: return false
        return !serverHealthRegistry.isHealthy(serverId)
    }
}
