package com.wordonline.matching.server.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("servers")
public class Server {
    
    @Id
    private Long id;
    
    private String serverUrl;
    
    private ServerType serverType;
    
    private boolean active;
}
