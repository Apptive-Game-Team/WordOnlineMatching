package com.wordonline.matching.quest.domain.reward;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.wordonline.matching.magic.service.MagicService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Scope("prototype")
@Component("magic_rg")
@RequiredArgsConstructor
public class MagicRewardGiver implements RewardGiver {

    private final MagicService magicService;

    @ParamName("magic_id")
    private Long magicId;

    @Override
    public Mono<Void> give(long userId) {
        return magicService.giveMagic(userId, magicId);
    }
}
