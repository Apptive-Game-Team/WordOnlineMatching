package com.wordonline.matching.matching.dto

import com.wordonline.matching.auth.dto.UserDetailResponseDto

data class MatchedInfoDto @JvmOverloads constructor(
    val message: String,
    val server: String,
    val leftUser: UserDetailResponseDto,
    val rightUser: UserDetailResponseDto,
    val sessionId: String,
    val type: String = "matchedInfoDto",
)
