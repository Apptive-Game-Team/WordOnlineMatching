package com.wordonline.matching.adventure.repository;

public record AdventureDatabaseDto(
        Long adventureId,
        String adventureState,
        Long stageId,
        String stageState,
        Long scenarioId,
        String scenarioState
) {}