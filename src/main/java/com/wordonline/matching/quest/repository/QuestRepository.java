package com.wordonline.matching.quest.repository;

import org.springframework.data.r2dbc.repository.R2dbcRepository;

import com.wordonline.matching.quest.entity.Quest;

public interface QuestRepository extends R2dbcRepository<Quest, Long> {

}
