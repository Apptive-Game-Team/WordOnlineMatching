package com.wordonline.matching.quest.domain.reward;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

@Scope("prototype")
@Component("card_rg")
public class CardRewardGiver implements RewardGiver {

    @ParamName("card_id")
    private Integer cardId;

    @Override
    public Mono<Void> give(long userId) {
        return null;
    }
}
