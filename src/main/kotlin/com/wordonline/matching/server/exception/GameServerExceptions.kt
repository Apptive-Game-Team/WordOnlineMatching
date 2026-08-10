package com.wordonline.matching.server.exception

/**
 * No game server could take a new session: either none is currently available, or every
 * candidate refused or failed.
 *
 * Distinct from a programming error on purpose. This is a capacity condition the client
 * should retry, so it maps to 503 rather than the 500 an `IllegalStateException` used to
 * produce.
 */
class NoAvailableGameServerException(message: String) : RuntimeException(message)

/**
 * The game server that hosts a known session did not answer.
 *
 * Kept separate from "session not found": an unreachable host says nothing about whether
 * the session is still alive, so the client must retry instead of being told its session
 * ended.
 */
class GameServerUnreachableException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
