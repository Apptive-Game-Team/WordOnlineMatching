package com.wordonline.matching.quest.domain.reward;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.wordonline.matching.deck.repository.UserCardRepository;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Scope("prototype")
@Component("card_rg")
@RequiredArgsConstructor
public class CardRewardGiver implements RewardGiver {

    private static final int CARD_REWARD_AMOUNT = 3;

    private final UserCardRepository userCardRepository;

    @ParamName("card_id")
    private Integer cardId;

    @Override
    public Mono<Void> give(long userId) {
        return userCardRepository.addCount(userId, (long) cardId, CARD_REWARD_AMOUNT);
    }

    @Override
    public String getRewardType() {
        return "CARD";
    }

    @Override
    public long getRewardId() {
        return cardId;
    }

    @Override
    public int getAmount() {
        return CARD_REWARD_AMOUNT;
    }
}
