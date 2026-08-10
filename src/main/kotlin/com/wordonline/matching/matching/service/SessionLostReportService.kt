package com.wordonline.matching.matching.service

import com.wordonline.matching.global.service.LocalizationService
import com.wordonline.matching.matching.domain.MatchTicket
import com.wordonline.matching.matching.domain.MatchTicketState
import com.wordonline.matching.matching.domain.SessionLiveness
import com.wordonline.matching.matching.domain.SessionLostReport
import com.wordonline.matching.matching.repository.MatchTicketRepository
import com.wordonline.matching.server.exception.GameServerUnreachableException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.reactor.ReactorContext
import org.slf4j.LoggerFactory
import org.springframework.context.i18n.LocaleContext
import org.springframework.context.i18n.SimpleLocaleContext
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.util.Locale

/**
 * Handles `POST /api/match/sessions/{sessionId}/report-lost`.
 *
 * The report is a hint, never a verdict: a client can only tell the lobby *where* to look.
 * Whether the session actually ended is decided by [SessionLivenessProbe] alone, so a
 * malicious or merely disconnected client cannot end a match that is still running.
 *
 * The path is session-scoped because the client reaches a match through the legacy flow and
 * holds a `sessionId` but no `ticketId`; the ticket is resolved here from the caller's id.
 */
@Service
class SessionLostReportService(
    private val matchTicketRepository: MatchTicketRepository,
    private val sessionLivenessProbe: SessionLivenessProbe,
    private val lostSessionRecovery: LostSessionRecovery,
    private val localizationService: LocalizationService,
) {
    private val clock = Clock.systemUTC()
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun report(userId: Long, sessionId: String): SessionLostReport {
        val ticket = matchTicketRepository.getActive(userId)
        if (ticket == null || ticket.matchInfo?.sessionId != sessionId) {
            return SessionLostReport.UnknownSession
        }
        // Nothing to release: the caller is already free and just has stale local state.
        if (ticket.state != MatchTicketState.MATCHED) {
            return SessionLostReport.NothingToRelease(ticket)
        }

        return when (sessionLivenessProbe.check(ticket)) {
            SessionLiveness.ALIVE -> {
                matchTicketRepository.clearPendingVerification(ticket.ticketId)
                SessionLostReport.SessionAlive(ticket)
            }
            SessionLiveness.UNKNOWN -> {
                // Deferring the verdict leaves the ticket MATCHED, and the client re-enters
                // the game scene on every MATCHED snapshot. Queue it for the fast re-check so
                // that bounce lasts seconds rather than a whole scan interval.
                matchTicketRepository.markPendingVerification(ticket.ticketId, Instant.now(clock))
                throw GameServerUnreachableException(localizedMessage())
            }
            SessionLiveness.LOST -> released(ticket, LostSessionRecovery.SESSION_LOST_REASON)
            // The client could not reach a session the host had already finished. Same
            // cleanup, but recording it as a loss would invent an incident that never was.
            SessionLiveness.ENDED -> released(ticket, LostSessionRecovery.SESSION_ENDED_REASON)
        }
    }

    private suspend fun released(ticket: MatchTicket, reason: String): SessionLostReport {
        val released = lostSessionRecovery.release(ticket, reason)
        if (released == null) {
            // The ticket changed under us; report whatever it is now rather than guessing.
            log.info("Ticket moved on while releasing lost session: ticketId={}", ticket.ticketId)
            val current = matchTicketRepository.getActive(ticket.userId)
                ?: return SessionLostReport.UnknownSession
            return SessionLostReport.NothingToRelease(current)
        }
        return SessionLostReport.Released(released)
    }

    /**
     * Mirrors the locale handling of the legacy session flow: the request locale rides in the
     * Reactor context, and its absence falls back to the default locale instead of throwing.
     */
    private suspend fun localizedMessage(): String {
        val localeContext: LocaleContext = currentCoroutineContext()[ReactorContext]
            ?.context
            ?.getOrEmpty<LocaleContext>(LocaleContext::class.java)
            ?.orElse(null)
            ?: SimpleLocaleContext(Locale.getDefault())
        return localizationService.getMessage(localeContext, "error.gameserver.unreachable")
    }
}
