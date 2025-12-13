package com.wordonline.matching.server.repository;

import org.springframework.data.r2dbc.repository.R2dbcRepository;

import com.wordonline.matching.server.domain.Server;
import com.wordonline.matching.server.domain.ServerState;
import com.wordonline.matching.server.domain.ServerType;

import reactor.core.publisher.Flux;

public interface ServerRepository extends R2dbcRepository<Server, Long> {

    Flux<Server> findAllByTypeAndState(ServerType type, ServerState state);
}
