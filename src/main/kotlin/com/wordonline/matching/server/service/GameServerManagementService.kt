package com.wordonline.matching.server.service

import com.wordonline.matching.server.client.GameServerClient
import com.wordonline.matching.server.entity.Server
import com.wordonline.matching.server.entity.ServerType
import com.wordonline.matching.session.repository.ServerRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.flow.toList
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * Discovers game servers and keeps their live availability up to date.
 *
 * Discovery reads every `GAME` row regardless of its persisted state, then the health
 * check decides which of them may receive a new session. That split is what lets a
 * redeployed game server - whose row was flipped to `INACTIVE` by its own shutdown hook -
 * come back on the next refresh instead of staying invisible for an hour.
 */
@Service
class GameServerManagementService(
    private val serverRepository: ServerRepository,
    private val gameServerClient: GameServerClient,
    private val serverHealthRegistry: ServerHealthRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        log.error("[Error] while refreshing game servers", throwable)
    }

    /** `SupervisorJob` so one failed refresh cannot tear down the scope of later ones. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)

    /** Every game server currently able to take a new session, in discovery order. */
    fun getAvailableServers(): List<Server> = serverHealthRegistry.availableServers()

    fun getAvailableServer(): Server? = getAvailableServers().firstOrNull()

    /**
     * Refreshes discovery and health together.
     *
     * `fixedDelay` rather than `fixedRate`: a slow refresh must not queue further refreshes
     * behind it. The annotation argument has to be a string, which is why this one setting
     * is read as a placeholder instead of through [com.wordonline.matching.server.config.GameServerProperties].
     */
    @Scheduled(fixedDelayString = "\${gameserver.refresh-interval}")
    fun load() {
        scope.launch { refresh() }
    }

    suspend fun refresh() {
        val discovered = serverRepository.findAllByType(ServerType.GAME).asFlow().toList()
        serverHealthRegistry.replaceServers(discovered)
        healthCheck()
    }

    /**
     * Probes every known server concurrently. Each probe swallows its own failure, so one
     * unreachable or slow server can no longer abort the sweep and leave the other servers'
     * health stale.
     */
    suspend fun healthCheck() = coroutineScope {
        serverHealthRegistry.servers
            .map { server -> async { probe(server) } }
            .awaitAll()
        Unit
    }

    private suspend fun probe(server: Server) {
        val healthy = try {
            // getUrl() throws when protocol/domain/port are null, so it belongs inside the guard.
            gameServerClient.healthcheck(server.url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Healthcheck error for game server {}: {}", server.id, e.toString())
            false
        }
        serverHealthRegistry.recordProbe(server.id, healthy)
    }
}
