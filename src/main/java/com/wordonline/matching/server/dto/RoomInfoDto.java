package com.wordonline.matching.server.dto;

public record RoomInfoDto(
        String sessionId,
        Long leftUserId,
        Long rightUserId,
        String serverUrl
) {

}
