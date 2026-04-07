package com.wordonline.matching.deploy.repository

import com.wordonline.matching.deploy.entity.DeployStatus
import org.springframework.data.r2dbc.repository.R2dbcRepository
import reactor.core.publisher.Mono

interface DeployStatusRepository : R2dbcRepository<DeployStatus, Long> {
    fun findByDeployType(deployType: String): Mono<DeployStatus>
}
