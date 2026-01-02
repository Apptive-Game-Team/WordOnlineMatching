package com.wordonline.matching.quest.repository;

import org.springframework.data.r2dbc.repository.R2dbcRepository;

import com.wordonline.matching.quest.entity.RewardParam;

public interface RewardParamRepository extends R2dbcRepository<RewardParam, Long> {

}
