package com.wordonline.matching.quest.domain.reward;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.wordonline.matching.decoration.entity.UserDecoration;
import com.wordonline.matching.decoration.repository.UserDecorationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Scope("prototype")
@Component("deco_rg")
@RequiredArgsConstructor
public class DecorationRewardGiver implements RewardGiver {

    private static final int DECORATION_REWARD_AMOUNT = 1;

    private final UserDecorationRepository userDecorationRepository;

    @ParamName("decoration_id")
    private Long decoId;

    @Override
    public Mono<Void> give(long userId) {
        var userDeco = new UserDecoration(null, userId, decoId, false);
        return userDecorationRepository.save(userDeco)
                .then();
    }

    @Override
    public String getRewardType() {
        return "DECORATION";
    }

    @Override
    public long getRewardId() {
        return decoId;
    }

    @Override
    public int getAmount() {
        return DECORATION_REWARD_AMOUNT;
    }
}
