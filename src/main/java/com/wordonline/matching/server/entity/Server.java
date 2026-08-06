package com.wordonline.matching.server.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import lombok.Getter;

@Getter
@Table(name = "servers")
public class Server {

    @Id
    private Long id;
    private String protocol;
    private String domain;
    private Integer port;
    private ServerState state;
    private ServerType type;

    public boolean isLocal() {
        return domain.equals("localhost") || domain.equals("127.0.0.1");
    }

    public String getUrl() {
        if (protocol == null || domain == null || port == null) {
            throw new IllegalStateException("Server protocol, domain, and port must not be null");
        }
        return String.format("%s://%s:%d", protocol, domain, port);
    }

    /**
     * A draining server finishes its running sessions but must never receive a new one.
     * <p>
     * Every other persisted {@link ServerState} is only a hint: live availability is
     * decided by the health check result held in
     * {@link com.wordonline.matching.server.service.ServerHealthRegistry}, not by this column.
     */
    public boolean isDraining() {
        return state == ServerState.DRAINING;
    }
}
