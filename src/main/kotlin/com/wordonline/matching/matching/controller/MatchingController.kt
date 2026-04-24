package com.wordonline.matching.matching.controller

import com.wordonline.matching.auth.service.UserId
import com.wordonline.matching.matching.dto.MatchedInfoDto
import com.wordonline.matching.matching.dto.QueueLengthResponseDto
import com.wordonline.matching.matching.dto.SimpleMessageDto
import com.wordonline.matching.matching.service.GameMatchService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
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

    @GetMapping("/api/match/queue/me/exist")
    suspend fun isMeInQueue(@UserId userId: Long?): ResponseEntity<Unit> {
        return if (gameMatchService.isInQueue(userId!!)) {
            ResponseEntity.ok().build()
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @DeleteMapping("/api/match/queue/me")
    suspend fun removeFromQueue(@UserId userId: Long?): ResponseEntity<Unit> {
        gameMatchService.removeFromQueue(userId!!)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/api/match/length")
    suspend fun getQueueLength(): QueueLengthResponseDto {
        return QueueLengthResponseDto(gameMatchService.getQueueLength().toInt())
    }
}