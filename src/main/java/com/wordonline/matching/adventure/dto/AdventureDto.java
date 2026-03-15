package com.wordonline.matching.adventure.dto;

import java.util.List;

public record AdventureDto(Long id, String state, List<StageDto> stages) {
}
