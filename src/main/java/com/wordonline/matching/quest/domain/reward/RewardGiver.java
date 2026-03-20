package com.wordonline.matching.quest.domain.reward;

import reactor.core.publisher.Mono;

public interface RewardGiver {

    Mono<Void> give(long userId);

    String getRewardType();

    long getRewardId();

    int getAmount();
}
