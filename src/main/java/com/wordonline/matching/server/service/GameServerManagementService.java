package com.wordonline.matching.server.service;

import java.util.List;
import java.util.Optional;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.wordonline.matching.server.client.GameServerClient;
import com.wordonline.matching.server.entity.Server;
import com.wordonline.matching.server.entity.ServerType;
import com.wordonline.matching.session.repository.ServerRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Discovers game servers and keeps their live availability up to date.
 * <p>
 * Discovery reads every {@code GAME} row regardless of its persisted state, then the
 * health check decides which of them may receive a new session. That split is what lets
 * a redeployed game server - whose row was flipped to {@code INACTIVE} by its own
 * shutdown hook - come back on the next refresh instead of staying invisible.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameServerManagementService {

    private final ServerRepository serverRepository;
    private final GameServerClient gameServerClient;
    private final ServerHealthRegistry serverHealthRegistry;

    /**
     * @return every game server currently able to take a new session, in discovery order.
     */
    public List<Server> getAvailableServers() {
        return serverHealthRegistry.getAvailableServers();
    }

    public Optional<Server> getAvailableServer() {
        return getAvailableServers().stream().findFirst();
    }

    /**
     * Refreshes discovery and health together.
     * <p>
     * {@code fixedDelay} rather than {@code fixedRate}: a slow refresh must not queue up
     * further refreshes behind it.
     */
    @Scheduled(fixedDelayString = "${gameserver.refresh-interval-ms:15000}")
    public void load() {
        refresh().subscribe(
                result -> {
                },
                error -> log.error("[Error] while refreshing game servers", error)
        );
    }

    Mono<Void> refresh() {
        return serverRepository.findAllByType(ServerType.GAME)
                .collectList()
                .doOnNext(serverHealthRegistry::replaceServers)
                .then(Mono.defer(this::healthCheck));
    }

    /**
     * Probes every known server. Each probe is isolated with its own
     * {@code onErrorResume}, so one unreachable or slow server can no longer abort the
     * whole {@link Flux} and leave the other servers' health stale.
     */
    Mono<Void> healthCheck() {
        return Flux.fromIterable(serverHealthRegistry.getServers())
                .flatMap(this::probe)
                .then();
    }

    private Mono<Boolean> probe(Server server) {
        return Mono.fromCallable(server::getUrl)
                .flatMap(gameServerClient::healthcheck)
                .defaultIfEmpty(false)
                .onErrorResume(error -> {
                    log.warn("Healthcheck error for game server {}: {}", server.getId(), error.toString());
                    return Mono.just(false);
                })
                .doOnNext(healthy -> serverHealthRegistry.recordProbe(server.getId(), healthy));
    }
}
