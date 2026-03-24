package com.wordonline.matching.matching.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.wordonline.matching.auth.service.UserId
import com.wordonline.matching.matching.dto.MatchedInfoDto
import com.wordonline.matching.matching.dto.PveRequest
import com.wordonline.matching.matching.dto.QueueLengthResponseDto
import com.wordonline.matching.matching.service.GameMatchService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class MatchingController(
    private val gameMatchService: GameMatchService,
    private val objectMapper: ObjectMapper
) {
    @GetMapping("/api/match/practice/me", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun matchPractice(@UserId userId: Long?): Flow<ServerSentEvent<String>> {
        return flow {
            val matchedInfo = gameMatchService.matchPractice(userId!!)
            emit(ServerSentEvent.builder(objectMapper.writeValueAsString(matchedInfo)).build())
        }
    }

    @GetMapping("/api/match/queue/me", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun match(@UserId userId: Long?): Flow<Any> {
        return gameMatchService.match(userId!!)
    }

    @GetMapping("/api/match/queue/me/exist")
    fun isMeInQueue(@UserId userId: Long?): ResponseEntity<Unit> {
        return if (gameMatchService.isInQueue(userId!!)) {
            ResponseEntity.ok().build()
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @DeleteMapping("/api/match/queue/me")
    suspend fun removeFromQueue(@UserId userId: Long?): ResponseEntity<Void> {
        gameMatchService.removeFromQueue(userId!!)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/api/match/length")
    fun getQueueLength(): QueueLengthResponseDto {
        return QueueLengthResponseDto(gameMatchService.getQueueLength())
    }
}