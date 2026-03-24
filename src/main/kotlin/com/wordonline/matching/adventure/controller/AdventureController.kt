package com.wordonline.matching.adventure.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.wordonline.matching.adventure.service.AdventureService
import com.wordonline.matching.auth.service.UserId
import com.wordonline.matching.matching.service.GameMatchService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class AdventureController(
    private val adventureService: AdventureService,
    private val gameMatchService: GameMatchService,
    private val objectMapper: ObjectMapper
) {

    @GetMapping("/adventures")
    fun getAdventures(@UserId userId: Long?) = adventureService.updateUserAdventures(userId!!)
        .then(adventureService.getAdventures(userId))


    @GetMapping(
        "/scenarios/{scenarioId}/play",
        produces = [MediaType.TEXT_EVENT_STREAM_VALUE]
    )
    fun playStage(
        @UserId userId: Long?,
        @PathVariable scenarioId: Long
    ): Flow<ServerSentEvent<String>> {
        return flow {
            val matchedInfo = gameMatchService.matchPVE(userId!!, scenarioId)
            emit(ServerSentEvent.builder(objectMapper.writeValueAsString(matchedInfo)).build())
        }
    }
}
