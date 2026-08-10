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
 * Retires the tickets of a session that is confirmed gone.
 *
 * Both players are stuck by the same missing session, and only one of them may ever report
 * it, so releasing one side alone would leave the other unable to re-queue. The opponent is
 * only touched while their active ticket still points at *this* session: once they have a
 * fresh ticket, a late report about the old session must not disturb it.
 */
@Service
class LostSessionRecovery(
    private val matchTicketRepository: MatchTicketRepository,
    private val userService: UserService,
) {
    companion object {
        const val SESSION_LOST_REASON = "SESSION_LOST"
    }

    private val clock = Clock.systemUTC()
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Transitions [ticket] - and the opponent's ticket when it qualifies - to
     * `FAILED(SESSION_LOST)`, then puts the released users back online.
     *
     * Returns the caller's ticket as it now stands. A `null` return means the ticket moved
     * on under us and nothing was changed.
     */
    suspend fun release(ticket: MatchTicket): MatchTicket? {
        val now = Instant.now(clock)
        val failed = ticket.toLost(now)
        val opponent = findOpponentOnSameSession(ticket)

        if (opponent != null && matchTicketRepository.transitionPair(failed, opponent.toLost(now), MatchTicketState.MATCHED)) {
            log.info(
                "Released both tickets of lost session: sessionId={}, userIds=[{}, {}]",
                ticket.matchInfo?.sessionId,
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
        log.info("Released ticket of lost session: sessionId={}, userId={}", ticket.matchInfo?.sessionId, ticket.userId)
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

    private fun MatchTicket.toLost(now: Instant) = copy(
        state = MatchTicketState.FAILED,
        version = version + 1,
        reason = SESSION_LOST_REASON,
        attemptId = null,
        allocationLeaseUntil = null,
        updatedAt = now,
    )

    private suspend fun markOnline(userId: Long) {
        userService.markOnline(userId).awaitSingleOrNull()
    }
}
