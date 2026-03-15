package com.wordonline.matching.data.service;

import com.wordonline.matching.data.domain.Parameter;
import com.wordonline.matching.data.dto.ParametersResponse;
import com.wordonline.matching.data.entity.GameObject;
import com.wordonline.matching.data.entity.ParameterValue;
import com.wordonline.matching.data.repository.GameObjectRepository;
import com.wordonline.matching.data.repository.ParameterRepository;
import com.wordonline.matching.data.repository.ParameterValueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class DataService {

    private final ParameterValueRepository parameterValueRepository;
    private final GameObjectRepository gameObjectRepository;
    private final ParameterRepository parameterRepository;

    public Mono<ParametersResponse> getParameters(String currentVersion) {
        Flux<ParameterValue> parameterValuesFlux;
        String initialVersion = currentVersion; // Store the original currentVersion for later

        if (currentVersion == null || currentVersion.isEmpty()) {
            parameterValuesFlux = parameterValueRepository.findAllParameters();
        } else {
            LocalDateTime timestamp = LocalDateTime.parse(currentVersion, DateTimeFormatter.ISO_DATE_TIME);
            parameterValuesFlux = parameterValueRepository.findAllUpdatedSince(timestamp);
        }

        return parameterValuesFlux
                .collectList()
                .flatMap(parameterValues -> {
                    if (parameterValues.isEmpty()) {
                        // If no parameters are found, return an empty list with the initial version or null
                        return Mono.just(new ParametersResponse(List.of(), initialVersion));
                    }

                    // Collect all game_object_ids and parameter_ids
                    List<Long> gameObjectIds = parameterValues.stream()
                            .map(ParameterValue::getGameObjectId)
                            .distinct()
                            .collect(Collectors.toList());
                    List<Long> parameterIds = parameterValues.stream()
                            .map(ParameterValue::getParameterId)
                            .distinct()
                            .collect(Collectors.toList());

                    // Fetch GameObjects and Parameters in parallel
                    Mono<Map<Long, GameObject>> gameObjectsMono = gameObjectRepository.findAllById(gameObjectIds)
                            .collectMap(GameObject::getId);
                    Mono<Map<Long, com.wordonline.matching.data.entity.Parameter>> parametersMono = parameterRepository.findAllById(parameterIds)
                            .collectMap(com.wordonline.matching.data.entity.Parameter::getId);

                    return Mono.zip(gameObjectsMono, parametersMono)
                            .map(tuple -> {
                                Map<Long, GameObject> gameObjectMap = tuple.getT1();
                                Map<Long, com.wordonline.matching.data.entity.Parameter> parameterMap = tuple.getT2();

                                LocalDateTime maxUpdatedAt = parameterValues.stream()
                                        .map(ParameterValue::getUpdatedAt)
                                        .filter(java.util.Objects::nonNull) // Filter out nulls if any
                                        .max(Comparator.naturalOrder())
                                        .orElse(null);

                                List<Parameter> domainParameters = parameterValues.stream()
                                        .map(pv -> new Parameter(
                                                gameObjectMap.getOrDefault(pv.getGameObjectId(), new GameObject()).getName(),
                                                parameterMap.getOrDefault(pv.getParameterId(), new com.wordonline.matching.data.entity.Parameter()).getName(),
                                                pv.getValue()
                                        ))
                                        .collect(Collectors.toList());

                                String version = (maxUpdatedAt != null) ? maxUpdatedAt.format(DateTimeFormatter.ISO_DATE_TIME) : initialVersion;
                                return new ParametersResponse(domainParameters, version);
                            });
                });
    }
}