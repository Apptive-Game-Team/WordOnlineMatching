package com.wordonline.matching.quest.service;

import java.lang.reflect.Field;

import org.springframework.beans.factory.BeanNotOfRequiredTypeException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.wordonline.matching.quest.domain.reward.ParamName;
import com.wordonline.matching.quest.domain.reward.RewardGiver;
import com.wordonline.matching.quest.entity.Quest;
import com.wordonline.matching.quest.repository.RewardParamRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuestRewardGiver {

    private final RewardParamRepository rewardParamRepository;
    private final ApplicationContext applicationContext;

    public Mono<Void> give(long userId, Quest quest) {
        RewardGiver rewardGiver;

        try {
            rewardGiver =
                    applicationContext.getBean(quest.getRewardGiver(), RewardGiver.class);
        } catch (NoSuchBeanDefinitionException | BeanNotOfRequiredTypeException e) {
            log.error("[Error] error while find reward giver", e);
            return Mono.empty();
        }

        return fillParam(rewardGiver, quest.getId())
                .then(rewardGiver.give(userId));
    }

    private Mono<Void> fillParam(RewardGiver rewardGiver, long questId) {
        return Flux.fromArray(rewardGiver.getClass().getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(ParamName.class))
                .flatMap(field -> {
                    field.setAccessible(true);
                    ParamName paramName = field.getAnnotation(ParamName.class);
                    return rewardParamRepository.findByQuestIdAndName(questId, paramName.value())
                            .publishOn(Schedulers.boundedElastic())
                            .flatMap(rewardParam -> {
                                try {
                                    field.set(rewardGiver, rewardParam.getValue());
                                } catch (IllegalAccessException e) {
                                    log.error("[Error] fill field with reflection");
                                    return Mono.error(e);
                                }
                                return Mono.empty();
                            })
                            .then();
                })
                .then();
    }
}
