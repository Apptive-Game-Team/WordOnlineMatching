package com.wordonline.matching.matching.domain

/**
 * Outcome of a client's "my session is gone" report.
 *
 * Modelled as a closed set so the controller maps every case to a status explicitly and a
 * new outcome cannot silently default to `200`.
 */
sealed interface SessionLostReport {
    /** The session was confirmed gone; the ticket is now `FAILED(SESSION_LOST)`. */
    data class Released(val ticket: MatchTicket) : SessionLostReport

    /** The session is still running. The ticket stays as it is. */
    data class SessionAlive(val ticket: MatchTicket) : SessionLostReport

    /** The ticket is not `MATCHED`, so the caller is already free. */
    data class NothingToRelease(val ticket: MatchTicket) : SessionLostReport

    /** No active ticket, or it belongs to a different session than the one reported. */
    data object UnknownSession : SessionLostReport
}
