package com.wordonline.matching.server.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import com.wordonline.matching.server.domain.Server;
import com.wordonline.matching.server.domain.ServerType;

import reactor.core.publisher.Flux;

public interface ServerRepository extends ReactiveCrudRepository<Server, Long> {
    
    @Query("SELECT * FROM servers WHERE server_type = :serverType AND active = true")
    Flux<Server> findByServerTypeAndActiveTrue(ServerType serverType);
}
