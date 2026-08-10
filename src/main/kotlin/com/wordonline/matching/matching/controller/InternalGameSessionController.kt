package com.wordonline.matching.matching.controller

import com.wordonline.matching.matching.dto.SessionEndedRequest
import com.wordonline.matching.matching.service.SessionEndedNotificationService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Server-to-server endpoints. No user ever calls these.
 *
 * The `/api/internal` prefix is not decoration: it is the path
 * [com.wordonline.matching.global.config.InternalApiSecurityConfig] matches, and everything
 * under it demands a service token instead of a user's JWT. Putting a user-facing handler
 * here would lock it out of the client; putting a service handler anywhere else would open
 * it to every logged-in player.
 */
@RestController
@RequestMapping("/api/internal/game-sessions")
class InternalGameSessionController(
    private val sessionEndedNotificationService: SessionEndedNotificationService,
) {
    /**
     * Tells the lobby a game session finished, so both tickets close now instead of waiting
     * for the reconciler sweep.
     *
     * Always `204` once accepted, including for a session the lobby has already closed or
     * has never heard of: the game is over on the caller's side either way, and an error
     * would only invite a retry loop that cannot change the outcome.
     */
    @PostMapping("/{sessionId}/ended")
    suspend fun sessionEnded(
        @PathVariable sessionId: String,
        @RequestBody request: SessionEndedRequest,
    ): ResponseEntity<Void> {
        val instanceId = request.instanceId
        require(!instanceId.isNullOrBlank()) { "instanceId is required" }
        sessionEndedNotificationService.sessionEnded(sessionId, instanceId)
        return ResponseEntity.noContent().build()
    }
}
