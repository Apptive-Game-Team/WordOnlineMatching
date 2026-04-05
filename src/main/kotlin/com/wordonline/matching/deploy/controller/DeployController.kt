package com.wordonline.matching.deploy.controller

import com.wordonline.matching.deploy.dto.DeployStatusResponse
import com.wordonline.matching.deploy.service.DeployStatusService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class DeployController(
    private val deployStatusService: DeployStatusService
) {
    @GetMapping("/deploy/status")
    suspend fun getStatus(): DeployStatusResponse = deployStatusService.getStatus()
}
