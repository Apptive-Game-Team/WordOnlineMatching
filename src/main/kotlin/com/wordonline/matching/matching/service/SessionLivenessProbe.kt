package com.wordonline.matching.matching.service

import com.wordonline.matching.matching.domain.MatchTicket
import com.wordonline.matching.matching.domain.SessionLiveness
import com.wordonline.matching.server.entity.Server
import com.wordonline.matching.server.exception.GameServerUnreachableException
import com.wordonline.matching.server.service.ServerHealthRegistry
import com.wordonline.matching.session.service.LegacyGameMatchService
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Decides whether the session behind a `MATCHED` ticket still exists.
 *
 * Two independent signals, cheapest first:
 *
 * 1. Boot generation. A game server keeps its sessions in memory only, so a process that
 *    restarted took them with it. The id the ticket recorded at match time versus the id
 *    the server row publishes now proves that without any network call - and it stays
 *    correct even when the restarted process is already answering health checks again.
 * 2. Session query against the hosting server. Authoritative when the host answers.
 *
 * Every inconclusive path collapses to [SessionLiveness.UNKNOWN]. A missing id on either
 * side is an older game server build, not evidence of a restart.
 */
@Component
class SessionLivenessProbe(
    private val legacyGameMatchService: LegacyGameMatchService,
    private val serverHealthRegistry: ServerHealthRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun check(ticket: MatchTicket): SessionLiveness {
        val matchInfo = ticket.matchInfo ?: return SessionLiveness.UNKNOWN

        if (hasRestarted(ticket)) {
            log.info(
                "Host process restarted since match: ticketId={}, sessionId={}, ticketInstanceId={}",
                ticket.ticketId,
                matchInfo.sessionId,
                ticket.serverInstanceId,
            )
            return SessionLiveness.LOST
        }

        return try {
            if (legacyGameMatchService.isSessionActive(matchInfo.server, matchInfo.sessionId)) {
                SessionLiveness.ALIVE
            } else {
                SessionLiveness.LOST
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GameServerUnreachableException) {
            log.warn("Host {} did not answer about session {}", matchInfo.server, matchInfo.sessionId, e)
            SessionLiveness.UNKNOWN
        }
    }

    /** Conclusive only when both sides reported an id and the ids differ. */
    private fun hasRestarted(ticket: MatchTicket): Boolean {
        val recorded = ticket.serverInstanceId ?: return false
        val current = hostOf(ticket)?.instanceId ?: return false
        return recorded != current
    }

    /**
     * The server row hosting this ticket's session.
     *
     * Matched by id first, which is what the ticket records at match time. The URL fallback
     * covers tickets written before [MatchTicket.serverId] existed.
     */
    fun hostOf(ticket: MatchTicket): Server? {
        val servers = serverHealthRegistry.servers
        ticket.serverId?.let { id -> servers.firstOrNull { it.id == id }?.let { return it } }
        val hostUrl = ticket.matchInfo?.server ?: return null
        return servers.firstOrNull { runCatching { it.url }.getOrNull() == hostUrl }
    }
}
