package com.wordonline.matching.session.domain

import com.wordonline.matching.matching.dto.MatchedInfoDto

/**
 * Where a session actually landed.
 *
 * The client only ever needs [matchInfo], but recovering a lost session needs to know which
 * game server row hosts it and which boot generation of that process accepted it. Those two
 * stay off [MatchedInfoDto] on purpose: they are lobby bookkeeping, not something the client
 * should see or be able to influence.
 */
data class SessionPlacement(
    val matchInfo: MatchedInfoDto,
    val serverId: Long?,
    val serverInstanceId: String?,
)
