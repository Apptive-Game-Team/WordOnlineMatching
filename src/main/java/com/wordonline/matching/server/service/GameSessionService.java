package com.wordonline.matching.server.service;

import org.springframework.stereotype.Service;

import com.wordonline.matching.server.client.GameServerClient;
import com.wordonline.matching.server.dto.RoomInfoDto;
import com.wordonline.matching.server.dto.RoomListDto;
import com.wordonline.matching.server.entity.Server;
import com.wordonline.matching.server.entity.ServerState;
import com.wordonline.matching.server.entity.ServerType;
import com.wordonline.matching.session.repository.ServerRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameSessionService {

    private final ServerRepository serverRepository;
    private final GameServerClient gameServerClient;

    public Mono<RoomListDto> getAllGameSessions() {
        return serverRepository.findAllByTypeAndState(ServerType.GAME, ServerState.ACTIVE)
                .flatMap(this::fetchGameSessionsFromServer)
                .collectList()
                .map(listOfLists -> {
                    // Each game server returns its own rooms oldest first, but flatMap interleaves the
                    // servers non-deterministically, so the merged list has to be re-sorted or the same
                    // set of rooms comes back in a different order on every request.
                    List<RoomInfoDto> allRooms = listOfLists.stream()
                            .flatMap(List::stream)
                            .sorted(Comparator.comparing(RoomInfoDto::createdAt,
                                            Comparator.nullsLast(Comparator.naturalOrder()))
                                    .thenComparing(RoomInfoDto::sessionId))
                            .toList();
                    return new RoomListDto(allRooms);
                });
    }

    private Mono<List<RoomInfoDto>> fetchGameSessionsFromServer(Server server) {
        // The outbound request goes over the server's call address (internal when reported,
        // public otherwise), but RoomInfoDto.serverUrl must stay the public url: it rides
        // MatchedInfoDto.server to the Unity client, which cannot reach the docker network.
        String callUrl = server.getCallUrl();
        String publicUrl = server.getUrl();

        return gameServerClient.getGameSessions(callUrl)
                .map(roomListDto -> {
                    // GameServerClient ensures roomListDto is never null and always contains a list (may be empty)
                    return roomListDto.rooms().stream()
                            .map(room -> new RoomInfoDto(
                                    room.sessionId(),
                                    room.leftUserId(),
                                    room.rightUserId(),
                                    publicUrl,
                                    room.createdAt()
                            ))
                            .toList();
                });
    }
}
