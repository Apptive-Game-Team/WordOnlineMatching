package com.wordonline.matching.adventure.dto;

import java.util.List;

public record StageDto(Long id, String state, List<ScenarioDto> scenarios) {
}
