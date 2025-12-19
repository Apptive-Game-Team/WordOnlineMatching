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

    public String getUrl() {
        if (protocol == null || domain == null || port == null) {
            throw new IllegalStateException("Server protocol, domain, and port must not be null");
        }
        return String.format("%s://%s:%d", protocol, domain, port);
    }

    public boolean isAvailable() {
        return state == ServerState.ACTIVE;
    }

    public ServerState updateState(boolean isActive) {
        if (isActive) {
            state = ServerState.ACTIVE;
        } else {
            state = ServerState.INACTIVE;
        }
        return state;
    }
}
