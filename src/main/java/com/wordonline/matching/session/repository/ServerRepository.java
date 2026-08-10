package com.wordonline.matching.session.repository;

import org.springframework.data.r2dbc.repository.R2dbcRepository;

import com.wordonline.matching.server.entity.Server;
import com.wordonline.matching.server.entity.ServerState;
import com.wordonline.matching.server.entity.ServerType;

import reactor.core.publisher.Flux;

public interface ServerRepository extends R2dbcRepository<Server, Long> {

    Flux<Server> findAllByTypeAndState(ServerType type, ServerState state);

    /**
     * Discovery query for the game server registry.
     * <p>
     * Deliberately unfiltered by {@link ServerState}: a game server redeploy flips its
     * row to {@code INACTIVE} on shutdown, and filtering here made the server invisible
     * to the lobby until the next reload. The persisted state is only a hint - the health
     * check decides real availability.
     */
    Flux<Server> findAllByType(ServerType type);
}
