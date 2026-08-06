package com.wordonline.matching.server.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Game server discovery and health check settings.
 *
 * Bound as a single object rather than scattered `@Value` fields so the values are
 * type-safe, discoverable in one place, and injectable into tests without reflection.
 */
@ConfigurationProperties(prefix = "gameserver")
data class GameServerProperties(

    /** How often the lobby re-discovers game servers and re-probes their health. */
    val refreshInterval: Duration = Duration.ofSeconds(15),

    /** Per-server health check timeout. A timeout counts as one failed probe, never an error. */
    val healthcheckTimeout: Duration = Duration.ofSeconds(3),

    /**
     * Consecutive failed probes before a server stops receiving new sessions.
     * One success restores it immediately.
     */
    val failureThreshold: Int = 3,
)
