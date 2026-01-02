package com.wordonline.matching.quest.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Table(name = "quests")
public class Quest {

    @Id
    private Long id;
    private String progressChecker;
    private Integer requireValue;
    private String rewardGiver;
}
