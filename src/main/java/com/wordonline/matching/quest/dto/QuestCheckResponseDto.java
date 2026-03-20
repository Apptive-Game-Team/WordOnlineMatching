package com.wordonline.matching.quest.dto;

import java.util.List;

public record QuestCheckResponseDto(
        List<QuestRewardDto> rewards
) {
}
