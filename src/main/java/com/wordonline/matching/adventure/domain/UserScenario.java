package com.wordonline.matching.adventure.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table("user_scenarios")
public class UserScenario {

    @Id
    private Long id;
    private Long userId;
    private Long scenarioId;
    private ContentState state;
}
