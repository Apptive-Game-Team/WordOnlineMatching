package com.wordonline.matching.server.service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.wordonline.matching.server.client.GameServerClient;
import com.wordonline.matching.server.entity.Server;
import com.wordonline.matching.server.entity.ServerState;
import com.wordonline.matching.server.entity.ServerType;
import com.wordonline.matching.session.repository.ServerRepository;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class GameServerManagementService {

    private final ServerRepository serverRepository;
    private final GameServerClient gameServerClient;

    private final List<Server> gameServers = new CopyOnWriteArrayList<>();

    public Optional<Server> getAvailableServer() {
        return gameServers.stream().filter(Server::isAvailable).findAny();
    }

    @Scheduled(fixedRate = 60 * 60 * 1000)
    public void load() {
        loadGameServer()
                .subscribe();
    }

    @Scheduled(fixedRate = 60 * 1000)
    public void update() {
        healthCheck()
                .subscribe();
    }

    private Mono<Void> loadGameServer() {
        return serverRepository.findAllByTypeAndState(ServerType.GAME, ServerState.ACTIVE)
                .collectList()
                .doOnNext(list -> {
                    gameServers.clear();
                    gameServers.addAll(list);
                })
                .then(healthCheck());
    }

    private Mono<Void> healthCheck() {
        return Flux.fromIterable(gameServers)
                .flatMap(server ->
                    gameServerClient.healthcheck(server.getUrl())
                            .map(server::updateState)
                )
                .then();
    }
}