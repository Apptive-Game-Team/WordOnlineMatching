package com.wordonline.matching.admin.controller

import com.wordonline.matching.admin.dto.BotMatchRequest
import com.wordonline.matching.admin.service.AdminMatchService
import com.wordonline.matching.matching.dto.MatchedInfoDto
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class AdminMatchController(
    private val adminMatchService: AdminMatchService,
) {
    @PostMapping("/api/admin/matches/bots")
    @PreAuthorize("hasAnyAuthority('WORDONLINE_ADMIN', 'SUPER_ADMIN')")
    suspend fun matchBots(@RequestBody request: BotMatchRequest): MatchedInfoDto {
        return adminMatchService.matchBots(request)
    }
}
