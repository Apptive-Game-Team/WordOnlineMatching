package com.wordonline.matching.matching.service

import com.wordonline.matching.auth.service.UserService
import com.wordonline.matching.matching.domain.MatchTicket
import com.wordonline.matching.matching.domain.MatchTicketState
import com.wordonline.matching.matching.repository.MatchTicketRepository
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

/**
 * Retires the tickets of a session that is confirmed over.
 *
 * Both players are stuck by the same missing session, and only one of them may ever report
 * it, so releasing one side alone would leave the other unable to re-queue. The opponent is
 * only touched while their active ticket still points at *this* session: once they have a
 * fresh ticket, a late report about the old session must not disturb it.
 *
 * A game that finished normally and a session that disappeared with its host need exactly
 * the same cleanup, so both come through here; only the recorded [reason] tells them apart.
 */
@Service
class LostSessionRecovery(
    private val matchTicketRepository: MatchTicketRepository,
    private val userService: UserService,
) {
    companion object {
        /** The session stopped existing without the host finishing it: a restart, or a dead host. */
        const val SESSION_LOST_REASON = "SESSION_LOST"

        /** The game ran to its end on a host that is still the process which accepted it. */
        const val SESSION_ENDED_REASON = "SESSION_ENDED"
    }

    private val clock = Clock.systemUTC()
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Transitions [ticket] - and the opponent's ticket when it qualifies - to
     * `FAILED(reason)`, then puts the released users back online.
     *
     * Returns the caller's ticket as it now stands. A `null` return means the ticket moved
     * on under us and nothing was changed.
     */
    suspend fun release(ticket: MatchTicket, reason: String = SESSION_LOST_REASON): MatchTicket? {
        val now = Instant.now(clock)
        val failed = ticket.toFailed(now, reason)
        val opponent = findOpponentOnSameSession(ticket)

        if (opponent != null &&
            matchTicketRepository.transitionPair(failed, opponent.toFailed(now, reason), MatchTicketState.MATCHED)
        ) {
            log.info(
                "Released both tickets of finished session: sessionId={}, reason={}, userIds=[{}, {}]",
                ticket.matchInfo?.sessionId,
                reason,
                ticket.userId,
                opponent.userId,
            )
            markOnline(ticket.userId)
            markOnline(opponent.userId)
            return failed
        }

        // Either there was no opponent to release, or their ticket changed between the read
        // and the write. The reporter still has to be freed.
        val released = matchTicketRepository.transition(failed, MatchTicketState.MATCHED) ?: return null
        log.info(
            "Released ticket of finished session: sessionId={}, reason={}, userId={}",
            ticket.matchInfo?.sessionId,
            reason,
            ticket.userId,
        )
        markOnline(ticket.userId)
        return released
    }

    private suspend fun findOpponentOnSameSession(ticket: MatchTicket): MatchTicket? {
        val sessionId = ticket.matchInfo?.sessionId ?: return null
        val opponentUserId = ticket.opponentUserId ?: return null
        val opponent = matchTicketRepository.getActive(opponentUserId) ?: return null
        val onSameSession = opponent.state == MatchTicketState.MATCHED &&
            opponent.ticketId != ticket.ticketId &&
            opponent.matchInfo?.sessionId == sessionId
        return opponent.takeIf { onSameSession }
    }

    private fun MatchTicket.toFailed(now: Instant, reason: String) = copy(
        state = MatchTicketState.FAILED,
        version = version + 1,
        reason = reason,
        attemptId = null,
        allocationLeaseUntil = null,
        updatedAt = now,
    )

    private suspend fun markOnline(userId: Long) {
        userService.markOnline(userId).awaitSingleOrNull()
    }
}
