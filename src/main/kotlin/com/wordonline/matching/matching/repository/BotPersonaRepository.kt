package com.wordonline.matching.matching.repository

import com.wordonline.matching.matching.domain.BotPersona
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.r2dbc.repository.R2dbcRepository
import reactor.core.publisher.Mono

interface BotPersonaRepository : R2dbcRepository<BotPersona, Long> {
    fun findByUserId(userId: Long): Mono<BotPersona>

    @Query(
        """
        SELECT user_id, name, enabled
        FROM bot_personas
        WHERE enabled = TRUE
        ORDER BY RANDOM()
        LIMIT 1
        """
    )
    fun findRandomEnabled(): Mono<BotPersona>
}
