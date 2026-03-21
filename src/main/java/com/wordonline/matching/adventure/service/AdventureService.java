package com.wordonline.matching.adventure.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.wordonline.matching.adventure.dto.AdventureDto;
import com.wordonline.matching.adventure.dto.AdventuresResponse;
import com.wordonline.matching.adventure.dto.ScenarioDto;
import com.wordonline.matching.adventure.dto.StageDto;
import com.wordonline.matching.adventure.repository.AdventureProjection;
import com.wordonline.matching.adventure.repository.UserScenarioRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AdventureService {

    private final UserScenarioRepository userScenarioRepository;
    private final AdventureInitService adventureInitService;
    private final AdventureProgressService adventureProgressService;

    public Mono<AdventuresResponse> getAdventures(Long userId) {
        return userScenarioRepository.findAllAdventureProgress(userId)
                .collectList()
                .map(this::mapToAdventuresResponse);
    }

    private AdventuresResponse mapToAdventuresResponse(List<AdventureProjection> adventureProjections) {
        Map<Long, List<AdventureProjection>> adventureMap = adventureProjections.stream()
                .collect(Collectors.groupingBy(AdventureProjection::getAdventureId, LinkedHashMap::new, Collectors.toList()));

        List<AdventureDto> adventureDtos = adventureMap.entrySet().stream().map(this::mapToAdventureDto).toList();

        return new AdventuresResponse(adventureDtos);
    }

    private AdventureDto mapToAdventureDto(Entry<Long, List<AdventureProjection>> adventureEntry) {
        List<AdventureProjection> advRows = adventureEntry.getValue();

        Map<Long, List<AdventureProjection>> stageMap = advRows.stream()
                .collect(Collectors.groupingBy(AdventureProjection::getStageId, LinkedHashMap::new, Collectors.toList()));

        List<StageDto> stageDtos = stageMap.entrySet().stream()
                .map(this::mapToStageDto).toList();

        return new AdventureDto(adventureEntry.getKey(), advRows.getFirst().getAdventureState(), stageDtos);
    }

    private StageDto mapToStageDto(Entry<Long, List<AdventureProjection>> stageEntry) {
        List<AdventureProjection> stageRows = stageEntry.getValue();

        List<ScenarioDto> scenarioDtos = stageRows.stream()
                .map(this::mapToScenarioDto)
                .toList();

        return new StageDto(stageEntry.getKey(), stageRows.getFirst().getStageState(), scenarioDtos);
    }

    private ScenarioDto mapToScenarioDto(AdventureProjection adventureProjection) {
        return new ScenarioDto(
                adventureProjection.getScenarioId(),
                adventureProjection.getScenarioState());
    }

    public Mono<Void> updateUserAdventures(long userId) {
        return adventureInitService.activateFreeAdventures(userId)
                .then(adventureProgressService.progressAllAdventures(userId));
    }
}