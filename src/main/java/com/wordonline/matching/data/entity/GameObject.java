package com.wordonline.matching.data.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table("game_objects")
public class GameObject {

    @Id
    private Long id;

    private String name;
}
