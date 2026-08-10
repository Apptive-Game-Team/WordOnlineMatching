package com.wordonline.matching.data.service;

import com.wordonline.matching.data.domain.Parameter;
import com.wordonline.matching.data.dto.ParametersResponse;
import com.wordonline.matching.data.entity.GameObject;
import com.wordonline.matching.data.entity.ParameterValue;
import com.wordonline.matching.data.repository.GameObjectRepository;
import com.wordonline.matching.data.repository.ParameterRepository;
import com.wordonline.matching.data.repository.ParameterValueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
@Transactional
public class DataService {

    private final ParameterValueRepository parameterValueRepository;
    private final GameObjectRepository gameObjectRepository;
    private final ParameterRepository parameterRepository;

    public Mono<ParametersResponse> getParameters(String currentVersion) {
        if (currentVersion == null || currentVersion.isEmpty()) {
            return buildParametersResponse(parameterValueRepository.findAllParameters(), null, true);
        }

        LocalDateTime timestamp = LocalDateTime.parse(currentVersion, DateTimeFormatter.ISO_DATE_TIME);
        return parameterValueRepository.findAllUpdatedSince(timestamp)
                .collectList()
                .flatMap(updatedParameterValues -> {
                    if (updatedParameterValues.isEmpty()) {
                        return Mono.just(new ParametersResponse(List.of(), currentVersion, false));
                    }
                    return buildParametersResponse(parameterValueRepository.findAllParameters(), null, true);
                });
    }

    private Mono<ParametersResponse> buildParametersResponse(
            Flux<ParameterValue> parameterValuesFlux,
            String fallbackVersion,
            boolean requiresRefresh
    ) {
        return parameterValuesFlux
                .collectList()
                .flatMap(parameterValues -> {
                    if (parameterValues.isEmpty()) {
                        return Mono.just(new ParametersResponse(List.of(), fallbackVersion, requiresRefresh));
                    }

                    // parameter_values.value is nullable in the database, but clients model the
                    // parameter value as a non-nullable number and fail to deserialize `null`.
                    // Drop those rows instead of shipping an entry the client cannot consume.
                    List<ParameterValue> serializableValues = parameterValues.stream()
                            .filter(parameterValue -> parameterValue.getValue() != null)
                            .collect(Collectors.toList());

                    int droppedCount = parameterValues.size() - serializableValues.size();
                    if (droppedCount > 0) {
                        log.warn("Dropped {} of {} parameter values with a null value from the parameters response",
                                droppedCount, parameterValues.size());
                    }

                    List<Long> gameObjectIds = serializableValues.stream()
                            .map(ParameterValue::getGameObjectId)
                            .distinct()
                            .collect(Collectors.toList());
                    List<Long> parameterIds = serializableValues.stream()
                            .map(ParameterValue::getParameterId)
                            .distinct()
                            .collect(Collectors.toList());

                    Mono<Map<Long, GameObject>> gameObjectsMono = gameObjectRepository.findAllById(gameObjectIds)
                            .collectMap(GameObject::getId);
                    Mono<Map<Long, com.wordonline.matching.data.entity.Parameter>> parametersMono = parameterRepository.findAllById(parameterIds)
                            .collectMap(com.wordonline.matching.data.entity.Parameter::getId);

                    return Mono.zip(gameObjectsMono, parametersMono)
                            .map(tuple -> {
                                Map<Long, GameObject> gameObjectMap = tuple.getT1();
                                Map<Long, com.wordonline.matching.data.entity.Parameter> parameterMap = tuple.getT2();

                                // Deliberately computed over the unfiltered rows: `findAllUpdatedSince`
                                // also sees the dropped rows, so a version derived from the filtered
                                // rows would stay behind a null row's `updated_at` forever and make a
                                // version-caching client re-fetch the full snapshot on every request.
                                LocalDateTime maxUpdatedAt = parameterValues.stream()
                                        .map(ParameterValue::getUpdatedAt)
                                        .filter(java.util.Objects::nonNull)
                                        .max(Comparator.naturalOrder())
                                        .orElse(null);

                                List<Parameter> domainParameters = serializableValues.stream()
                                        .map(pv -> new Parameter(
                                                gameObjectMap.getOrDefault(pv.getGameObjectId(), new GameObject()).getName(),
                                                parameterMap.getOrDefault(pv.getParameterId(), new com.wordonline.matching.data.entity.Parameter()).getName(),
                                                pv.getValue()
                                        ))
                                        .collect(Collectors.toList());

                                String version = (maxUpdatedAt != null)
                                        ? maxUpdatedAt.format(DateTimeFormatter.ISO_DATE_TIME)
                                        : fallbackVersion;
                                return new ParametersResponse(domainParameters, version, requiresRefresh);
                            });
                });
    }
}
