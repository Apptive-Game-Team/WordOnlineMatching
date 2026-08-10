package com.wordonline.matching.matching.domain

/**
 * What the lobby could establish about the session behind a `MATCHED` ticket.
 *
 * [UNKNOWN] is a first-class answer, not a failure to be folded into [LOST]. A game server
 * that does not answer says nothing about whether its sessions are still running, and
 * treating silence as proof of loss would end live matches during a network blip.
 */
enum class SessionLiveness {
    ALIVE,
    LOST,
    UNKNOWN,
}
