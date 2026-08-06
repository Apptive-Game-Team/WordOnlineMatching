package com.wordonline.matching.server.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.wordonline.matching.server.entity.Server;

import lombok.extern.slf4j.Slf4j;

/**
 * Holds the discovered game server list together with the health history of each server.
 * <p>
 * Two invariants this class exists to guarantee:
 * <ul>
 *     <li>The server list is swapped atomically. Readers always observe a complete
 *     snapshot, never the empty window a {@code clear()} + {@code addAll()} pair leaves
 *     behind.</li>
 *     <li>Health history is keyed by server id and lives outside the {@link Server}
 *     entity, so reloading the list from the database does not reset the consecutive
 *     failure counters of servers that survived the reload.</li>
 * </ul>
 */
@Slf4j
@Component
public class ServerHealthRegistry {

    private final int failureThreshold;

    private final Map<Long, HealthState> healthStates = new ConcurrentHashMap<>();

    /**
     * Immutable snapshot, replaced wholesale. {@code volatile} publishes the new list
     * safely to reader threads.
     */
    private volatile List<Server> servers = List.of();

    public ServerHealthRegistry(@Value("${gameserver.failure-threshold:3}") int failureThreshold) {
        this.failureThreshold = Math.max(1, failureThreshold);
    }

    /**
     * Atomically replaces the known server list.
     * <p>
     * Health history of servers still present is kept; history of servers that vanished
     * from the database is dropped so the map cannot grow without bound.
     */
    public void replaceServers(List<Server> discovered) {
        List<Server> snapshot = List.copyOf(discovered);
        this.servers = snapshot;

        Set<Long> knownIds = new HashSet<>();
        for (Server server : snapshot) {
            knownIds.add(server.getId());
        }
        healthStates.keySet().retainAll(knownIds);
    }

    public List<Server> getServers() {
        return servers;
    }

    /**
     * Records a single health probe result.
     * <p>
     * Hysteresis: one success restores a server immediately, while it takes
     * {@code failure-threshold} consecutive failures to take one out of rotation.
     */
    public void recordProbe(Long serverId, boolean healthy) {
        if (serverId == null) {
            return;
        }
        HealthState state = healthStates.computeIfAbsent(serverId, id -> new HealthState());
        boolean wasHealthy = state.isHealthy();
        state.record(healthy, failureThreshold);
        boolean isHealthy = state.isHealthy();

        if (wasHealthy != isHealthy) {
            log.info("Game server {} is now {} (consecutive failures: {})",
                    serverId, isHealthy ? "available" : "unavailable", state.consecutiveFailures());
        }
    }

    /**
     * A server is healthy only once a probe has actually succeeded. An unprobed - or
     * never yet successful - server counts as unavailable, because the persisted state
     * column is not trustworthy.
     */
    public boolean isHealthy(Long serverId) {
        HealthState state = healthStates.get(serverId);
        return state != null && state.isHealthy();
    }

    public int consecutiveFailures(Long serverId) {
        HealthState state = healthStates.get(serverId);
        return state == null ? 0 : state.consecutiveFailures();
    }

    /**
     * @return every server that passed its health check and is not draining.
     */
    public List<Server> getAvailableServers() {
        List<Server> snapshot = servers;
        List<Server> available = new ArrayList<>(snapshot.size());
        for (Server server : snapshot) {
            if (server.isDraining()) {
                continue;
            }
            if (isHealthy(server.getId())) {
                available.add(server);
            }
        }
        return List.copyOf(available);
    }

    private static final class HealthState {

        /** Starts unhealthy: a server earns availability by answering, not by existing. */
        private boolean healthy;
        private int consecutiveFailures;

        synchronized void record(boolean probeSucceeded, int failureThreshold) {
            if (probeSucceeded) {
                consecutiveFailures = 0;
                healthy = true;
                return;
            }
            consecutiveFailures++;
            if (consecutiveFailures >= failureThreshold) {
                healthy = false;
            }
        }

        synchronized boolean isHealthy() {
            return healthy;
        }

        synchronized int consecutiveFailures() {
            return consecutiveFailures;
        }
    }
}
