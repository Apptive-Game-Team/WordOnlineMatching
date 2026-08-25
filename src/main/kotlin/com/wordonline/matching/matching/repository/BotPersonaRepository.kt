package com.wordonline.matching.matching.repository

import com.wordonline.matching.matching.domain.BotPersona
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.r2dbc.repository.R2dbcRepository
import reactor.core.publisher.Mono

interface BotPersonaRepository : R2dbcRepository<BotPersona, Long> {
    fun findByUserId(userId: Long): Mono<BotPersona>

    // Excludes the hospitality bot on purpose. It is enabled, so without this clause an ordinary
    // player would occasionally draw the opponent that is built to lose, which is not a practice
    // match at all.
    @Query(
        """
        SELECT user_id, name, enabled, hospitality
        FROM bot_personas
        WHERE enabled = TRUE
          AND hospitality = FALSE
        ORDER BY RANDOM()
        LIMIT 1
        """
    )
    fun findRandomEnabled(): Mono<BotPersona>

    @Query(
        """
        SELECT user_id, name, enabled, hospitality
        FROM bot_personas
        WHERE enabled = TRUE
          AND hospitality = TRUE
        ORDER BY user_id
        LIMIT 1
        """
    )
    fun findHospitality(): Mono<BotPersona>
}
