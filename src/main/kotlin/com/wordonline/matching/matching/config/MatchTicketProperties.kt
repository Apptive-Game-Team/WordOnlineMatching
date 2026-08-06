package com.wordonline.matching.matching.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("matching.ticket")
data class MatchTicketProperties(
    val queueTimeout: Duration = Duration.ofMinutes(5),
    val allocationLease: Duration = Duration.ofSeconds(30),
    val terminalTtl: Duration = Duration.ofMinutes(30),
)
