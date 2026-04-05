package com.wordonline.matching.deploy.service

import com.wordonline.matching.deploy.dto.DeployStatusResponse
import com.wordonline.matching.deploy.repository.DeployStatusRepository
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class DeployStatusService(
    private val deployStatusRepository: DeployStatusRepository,
    @Value("\${deploy.type}") private val deployType: String
) {
    companion object {
        private const val DEFAULT_STATUS = "Healthy"
    }

    suspend fun getStatus(): DeployStatusResponse {
        val deployStatus = deployStatusRepository.findByDeployType(deployType).awaitSingleOrNull()
            ?: return DeployStatusResponse(status = DEFAULT_STATUS)
        return DeployStatusResponse(status = deployStatus.status)
    }
}
