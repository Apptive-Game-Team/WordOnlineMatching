package com.wordonline.matching.matching.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table

@Table("bot_personas")
data class BotPersona(
    @Id @Column("user_id") val userId: Long,
    val name: String,
    val enabled: Boolean,
)
