package com.wordonline.matching.adventure.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.wordonline.matching.adventure.domain.ContentState;
import com.wordonline.matching.adventure.dto.AdventureDto;
import com.wordonline.matching.adventure.dto.AdventuresResponse;
import com.wordonline.matching.adventure.dto.ScenarioDto;
import com.wordonline.matching.adventure.dto.StageDto;
import com.wordonline.matching.adventure.repository.AdventureDatabaseDto;
import com.wordonline.matching.adventure.repository.UserAdventureRepository;
import com.wordonline.matching.adventure.repository.UserScenarioRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AdventureService {

    private final UserAdventureRepository userAdventureRepository;
    private final UserScenarioRepository userScenarioRepository;
    private final AdventureInitService adventureInitService;
    private final AdventureProgressService adventureProgressService;

    public Mono<Boolean> isScenarioUnlocked(long userId, long scenarioId) {
        return updateUserAdventures(userId)
                .then(userScenarioRepository.findByUserIdAndScenarioId(userId, scenarioId))
                .map(userScenario -> userScenario.getState() != ContentState.INACTIVE)
                .defaultIfEmpty(false);
    }

    public Mono<AdventuresResponse> getAdventures(Long userId) {
        return userAdventureRepository.findAllAdventureProgress(userId)
                .collectList()
                .map(this::mapToAdventuresResponse);
    }

    private AdventuresResponse mapToAdventuresResponse(List<AdventureDatabaseDto> AdventureDatabaseDtos) {
        Map<Long, List<AdventureDatabaseDto>> adventureMap = AdventureDatabaseDtos.stream()
                .collect(Collectors.groupingBy(AdventureDatabaseDto::adventureId, LinkedHashMap::new, Collectors.toList()));

        List<AdventureDto> adventureDtos = adventureMap.entrySet().stream().map(this::mapToAdventureDto).toList();

        return new AdventuresResponse(adventureDtos);
    }

    private AdventureDto mapToAdventureDto(Entry<Long, List<AdventureDatabaseDto>> adventureEntry) {
        List<AdventureDatabaseDto> advRows = adventureEntry.getValue();

        Map<Long, List<AdventureDatabaseDto>> stageMap = advRows.stream()
                .collect(Collectors.groupingBy(AdventureDatabaseDto::stageId, LinkedHashMap::new, Collectors.toList()));

        List<StageDto> stageDtos = stageMap.entrySet().stream()
                .map(this::mapToStageDto).toList();

        return new AdventureDto(adventureEntry.getKey(), advRows.getFirst().adventureState(), stageDtos);
    }

    private StageDto mapToStageDto(Entry<Long, List<AdventureDatabaseDto>> stageEntry) {
        List<AdventureDatabaseDto> stageRows = stageEntry.getValue();

        List<ScenarioDto> scenarioDtos = stageRows.stream()
                .map(this::mapToScenarioDto)
                .toList();

        return new StageDto(stageEntry.getKey(), stageRows.getFirst().stageState(), scenarioDtos);
    }

    private ScenarioDto mapToScenarioDto(AdventureDatabaseDto AdventureDatabaseDto) {
        return new ScenarioDto(
                AdventureDatabaseDto.scenarioId(),
                AdventureDatabaseDto.scenarioState());
    }

    public Mono<Void> updateUserAdventures(long userId) {
        return adventureInitService.activateFreeAdventures(userId)
                .then(adventureProgressService.progressAllAdventures(userId));
    }
}