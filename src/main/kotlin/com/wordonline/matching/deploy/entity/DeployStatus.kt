package com.wordonline.matching.deploy.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table

@Table("deploy_status")
data class DeployStatus(
    @Id val id: Long? = null,
    @Column("deploy_type") val deployType: String,
    val status: String
)
