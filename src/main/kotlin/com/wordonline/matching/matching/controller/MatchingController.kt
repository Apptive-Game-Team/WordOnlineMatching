package com.wordonline.matching.matching.controller

import com.wordonline.matching.auth.service.UserId
import com.wordonline.matching.matching.dto.MatchedInfoDto
import com.wordonline.matching.matching.dto.QueueLengthResponseDto
import com.wordonline.matching.matching.dto.SimpleMessageDto
import com.wordonline.matching.matching.service.GameMatchService
import com.wordonline.matching.matching.domain.CancelMatchResponse
import com.wordonline.matching.matching.domain.MatchTicket
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class MatchingController(
    private val gameMatchService: GameMatchService,
) {
    @GetMapping("/api/match/practice/me")
    suspend fun matchPractice(@UserId userId: Long?): MatchedInfoDto {
        return gameMatchService.matchPractice(userId!!)
    }

    @GetMapping("/api/match/queue/me")
    suspend fun match(@UserId userId: Long?): SimpleMessageDto {
        return gameMatchService.match(userId!!)
    }

    @PostMapping("/api/match/tickets")
    suspend fun createTicket(@UserId userId: Long?): MatchTicket = gameMatchService.createTicket(userId!!)

    @GetMapping("/api/match/queue/me/exist")
    suspend fun isMeInQueue(@UserId userId: Long?): ResponseEntity<Unit> {
        return if (gameMatchService.isInQueue(userId!!)) {
            ResponseEntity.ok().build()
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @DeleteMapping("/api/match/queue/me")
    suspend fun removeFromQueue(@UserId userId: Long?): CancelMatchResponse = gameMatchService.removeFromQueue(userId!!)

    @DeleteMapping("/api/match/tickets/{ticketId}")
    suspend fun cancelTicket(
        @UserId userId: Long?,
        @PathVariable ticketId: String,
    ): CancelMatchResponse = gameMatchService.cancelTicket(userId!!, ticketId)

    @GetMapping("/api/match/tickets/active")
    suspend fun getActiveTicket(@UserId userId: Long?): ResponseEntity<MatchTicket> {
        val ticket = gameMatchService.getActiveTicket(userId!!)
        return if (ticket == null) ResponseEntity.notFound().build() else ResponseEntity.ok(ticket)
    }

    @GetMapping("/api/match/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun events(@UserId userId: Long?): Flow<ServerSentEvent<MatchTicket>> = gameMatchService.events(userId!!).map { ticket ->
        ServerSentEvent.builder(ticket)
            .id(ticket.version.toString())
            .event("match-ticket-updated")
            .build()
    }

    @GetMapping("/api/match/length")
    suspend fun getQueueLength(): QueueLengthResponseDto {
        return QueueLengthResponseDto(gameMatchService.getQueueLength().toInt())
    }
}
