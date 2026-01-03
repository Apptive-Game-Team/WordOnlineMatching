package com.wordonline.matching.quest.repository;

import org.springframework.data.r2dbc.repository.R2dbcRepository;

import com.wordonline.matching.quest.entity.RewardParam;

import reactor.core.publisher.Mono;

public interface RewardParamRepository extends R2dbcRepository<RewardParam, Long> {

    Mono<RewardParam> findByQuestIdAndName(Long questId, String name);

    Mono<RewardParam> findByNameAndValue(String name, Integer value);
}
