package com.wordonline.matching.session.entity;

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
        return String.format("%s://%s:%d", protocol, domain, port);
    }
}

