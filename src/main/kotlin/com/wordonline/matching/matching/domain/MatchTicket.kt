package com.wordonline.matching.matching.domain

import com.wordonline.matching.matching.dto.MatchedInfoDto
import java.time.Instant

enum class MatchTicketState {
    QUEUED,
    ALLOCATING,
    MATCHED,
    CANCELED,
    EXPIRED,
    FAILED,
}

data class MatchTicket(
    val ticketId: String,
    val userId: Long,
    val mmr: Long,
    val state: MatchTicketState,
    val version: Long,
    val reason: String? = null,
    val matchInfo: MatchedInfoDto? = null,
    val attemptId: String? = null,
    val allocationLeaseUntil: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

enum class CancelMatchResult {
    CANCELED,
    TOO_LATE,
    ALREADY_FINISHED,
    NOT_FOUND,
}

data class CancelMatchResponse(
    val result: CancelMatchResult,
    val ticket: MatchTicket?,
)
