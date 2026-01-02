package com.wordonline.matching.quest.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import com.wordonline.matching.quest.domain.QuestState;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table("user_quests")
public class UserQuest {

    @Id
    private Long id;
    private Long questId;
    private Long userId;
    private QuestState state;
}
