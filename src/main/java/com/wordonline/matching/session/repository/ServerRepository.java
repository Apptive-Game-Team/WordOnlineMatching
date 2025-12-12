package com.wordonline.matching.session.repository;

import org.springframework.data.r2dbc.repository.R2dbcRepository;

import com.wordonline.matching.session.entity.Server;
import com.wordonline.matching.session.entity.ServerState;

import reactor.core.publisher.Flux;

public interface ServerRepository extends R2dbcRepository<Server, Long> {

    Flux<Server> findAllByState(ServerState state);
}
