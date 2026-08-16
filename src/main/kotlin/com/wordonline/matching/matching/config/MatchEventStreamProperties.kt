package com.wordonline.matching.matching.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("matching.events")
data class MatchEventStreamProperties(
    /**
     * How often `/api/match/events` emits a keep-alive comment. Ticket updates are rare, so
     * without traffic a proxy or load balancer closes the idle stream and the client stops
     * hearing about its own match. Keep it below the shortest idle timeout in front of this
     * service.
     */
    val heartbeatInterval: Duration = Duration.ofSeconds(15),
)
