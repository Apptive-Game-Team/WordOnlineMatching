package com.wordonline.matching.quest.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Table("reward_param")
public class RewardParam {

    @Id
    private Long id;
    private Long questId;
    private String name;
    private Integer value;
}
