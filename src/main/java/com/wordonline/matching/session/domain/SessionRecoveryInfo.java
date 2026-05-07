package com.wordonline.matching.session.domain;

import java.sql.Time;

import com.wordonline.matching.matching.dto.MatchedInfoDto;

public record SessionRecoveryInfo(
        long leftUserId,
        long rightUserId,
        String sessionId,
        String serverUrl,
        long expireAt
) {

    private final static long TTL = 5 * 60 * 1000;

    public SessionRecoveryInfo(MatchedInfoDto matchedInfoDto) {
        this(
                matchedInfoDto.getLeftUser().id(),
                matchedInfoDto.getRightUser().id(),
                matchedInfoDto.getSessionId(),
                matchedInfoDto.getServer(),
                System.currentTimeMillis() + TTL
        );
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expireAt;
    }
}
