package com.wordonline.matching.quest.dto;

public record QuestRewardDto(
        String rewardType,
        long rewardId,
        int amount,
        long questId
) {
}
