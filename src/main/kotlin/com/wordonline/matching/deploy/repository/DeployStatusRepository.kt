package com.wordonline.matching.deploy.repository

import com.wordonline.matching.deploy.entity.DeployStatus
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Mono

interface DeployStatusRepository : ReactiveCrudRepository<DeployStatus, Long> {
    fun findByDeployType(deployType: String): Mono<DeployStatus>
}
