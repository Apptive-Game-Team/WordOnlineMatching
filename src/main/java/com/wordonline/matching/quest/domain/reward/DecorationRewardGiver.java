package com.wordonline.matching.quest.domain.reward;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.wordonline.matching.decoration.entity.UserDecoration;
import com.wordonline.matching.decoration.repository.UserDecorationRepository;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Scope("prototype")
@Component("deco_rg")
@RequiredArgsConstructor
public class DecorationRewardGiver implements RewardGiver {

    private final UserDecorationRepository userDecorationRepository;

    @ParamName("decoration_id")
    private Long decoId;

    @Override
    public Mono<Void> give(long userId) {
        var userDeco = new UserDecoration(null, userId, decoId, false);
        return userDecorationRepository.save(userDeco)
                .then();
    }
}
