package com.wordonline.matching.matching.dto;

public record SessionDto(
        String sessionId,
        Long uid1,
        Long uid2,
        SessionType sessionType,
        Long scenarioId
) {

    public static SessionDto PVP(String sessionId, Long uid1, Long uid2) {
        return new SessionDto(sessionId, uid1, uid2, SessionType.PVP, null);
    }

    public static SessionDto PVE(String sessionId, Long userId, Long scenarioId) {
        return new SessionDto(sessionId, userId, null, SessionType.PVE, scenarioId);
    }

    public static SessionDto Practice(String sessionId, Long uid1, Long uid2) {
        return new SessionDto(sessionId, uid1, uid2, SessionType.Practice, null);
    }

    public static SessionDto from(String sessionId, long uid1, long uid2) {
        if (uid2 < 0) {
            return SessionDto.Practice(sessionId, uid1, uid2);
        } else {
            return SessionDto.PVP(sessionId, uid1, uid2);
        }
    }
}

