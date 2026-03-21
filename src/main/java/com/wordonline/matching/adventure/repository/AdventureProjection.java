package com.wordonline.matching.adventure.repository;

public interface AdventureProjection {
    Long getAdventureId();
    String getAdventureState();
    Long getStageId();
    String getStageState();
    Long getScenarioId();
    String getScenarioState();
}