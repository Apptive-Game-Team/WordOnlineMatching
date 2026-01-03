package com.wordonline.matching.quest.dto;

import com.wordonline.matching.quest.domain.QuestState;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class QuestProgressResponseDto {
    private final QuestState state;
    private final int progress;
    private final int requireValue;
}
