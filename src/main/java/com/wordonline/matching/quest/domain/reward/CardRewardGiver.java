package com.wordonline.matching.quest.domain.reward;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.wordonline.matching.deck.domain.UserCard;
import com.wordonline.matching.deck.repository.UserCardRepository;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Scope("prototype")
@Component("card_rg")
@RequiredArgsConstructor
public class CardRewardGiver implements RewardGiver {

    private final UserCardRepository userCardRepository;

    @ParamName("card_id")
    private Integer cardId;

    @Override
    public Mono<Void> give(long userId) {
        var userCard = new UserCard(userId, (long) cardId, 3);
        return userCardRepository.save(userCard)
                .then();
    }
}
