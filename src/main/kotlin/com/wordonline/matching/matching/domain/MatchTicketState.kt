package com.wordonline.matching.matching.domain

enum class MatchTicketState {
    /** Waiting in the matchmaking queue. */
    QUEUED,

    /** Paired by a matching tick, game session not confirmed yet. */
    MATCHED,

    /** Game session confirmed on a game server. */
    PLAYING,
}
