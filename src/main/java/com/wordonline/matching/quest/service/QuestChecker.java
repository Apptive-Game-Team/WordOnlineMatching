package com.wordonline.matching.quest.service;

import org.springframework.beans.factory.BeanNotOfRequiredTypeException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.wordonline.matching.quest.domain.checker.ProgressChecker;
import com.wordonline.matching.quest.entity.Quest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuestChecker {

    private final ApplicationContext applicationContext;

    public Mono<Boolean> check(long userId, Quest quest) {
        ProgressChecker progressChecker;

        try {
            progressChecker =
                    applicationContext.getBean(quest.getProgressChecker(), ProgressChecker.class);
        } catch (NoSuchBeanDefinitionException | BeanNotOfRequiredTypeException e) {
            log.error("[Error] error while find progress checker", e);
            return Mono.just(false);
        }

       return progressChecker.check(userId).map(progress -> progress >= quest.getRequireValue());
    }
}
