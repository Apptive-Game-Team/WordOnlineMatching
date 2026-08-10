package com.wordonline.matching.matching.domain

/**
 * What the lobby could establish about the session behind a `MATCHED` ticket.
 *
 * [ENDED] and [LOST] are handled identically - both retire the ticket pair and put the users
 * back online - but they are not the same event and must not be recorded as one. A game that
 * simply finished is normal operation; a host process that took its in-memory sessions with
 * it is an incident, and collapsing the two hides every restart inside the noise of ordinary
 * match endings.
 *
 * [UNKNOWN] is a first-class answer, not a failure to be folded into [LOST]. A game server
 * that does not answer says nothing about whether its sessions are still running, and
 * treating silence as proof of loss would end live matches during a network blip.
 */
enum class SessionLiveness {
    ALIVE,

    /**
     * The hosting process is provably the same one that accepted the session, and it says the
     * session is no longer there. The game finished.
     */
    ENDED,

    /**
     * The session is gone without the host having finished it: the boot generation changed,
     * or the host is gone entirely. Whether the game reached its end is unknowable.
     */
    LOST,

    UNKNOWN,
}
