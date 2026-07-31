package com.wordonline.matching.matching.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

/**
 * Single source of truth for one user's matchmaking state.
 * Queue membership, pairing result and session recovery info all live in this row.
 */
@Table("match_tickets")
data class MatchTicket(
    @Id val userId: Long,
    val mmr: Long,
    val state: MatchTicketState,
    val enqueuedAt: Instant,
    val updatedAt: Instant,
    val sessionId: String?,
    val serverUrl: String?,
    val leftUserId: Long?,
    val rightUserId: Long?,
    val retryCount: Int,
)
