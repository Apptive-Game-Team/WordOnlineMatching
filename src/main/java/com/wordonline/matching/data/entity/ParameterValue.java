package com.wordonline.matching.data.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "parameter_values")
public class ParameterValue {

    @Id
    private Long id;

    @Column("parameter_id")
    private Long parameterId;

    @Column("game_object_id")
    private Long gameObjectId;

    private Double value;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
