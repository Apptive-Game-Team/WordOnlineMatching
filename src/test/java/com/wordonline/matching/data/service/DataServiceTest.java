package com.wordonline.matching.data.service;

import com.wordonline.matching.data.dto.ParametersResponse;
import com.wordonline.matching.data.entity.GameObject;
import com.wordonline.matching.data.entity.Parameter;
import com.wordonline.matching.data.entity.ParameterValue;
import com.wordonline.matching.data.repository.GameObjectRepository;
import com.wordonline.matching.data.repository.ParameterRepository;
import com.wordonline.matching.data.repository.ParameterValueRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataServiceTest {

    @Mock
    private ParameterValueRepository parameterValueRepository;

    @Mock
    private GameObjectRepository gameObjectRepository;

    @Mock
    private ParameterRepository parameterRepository;

    @InjectMocks
    private DataService dataService;

    @Test
    @DisplayName("버전_조회시_변경된_게임오브젝트의_전체_파라미터를_반환")
    void getParameters_WithVersion_ReturnsAllParametersForChangedGameObjects() {
        String currentVersion = "2024-01-01T00:00:00";
        LocalDateTime oldUpdatedAt = LocalDateTime.parse("2024-01-01T00:00:00");
        LocalDateTime newUpdatedAt = LocalDateTime.parse("2024-01-02T12:00:00");

        ParameterValue unchangedValue = ParameterValue.builder()
                .id(1L)
                .gameObjectId(10L)
                .parameterId(100L)
                .value(100.0)
                .updatedAt(oldUpdatedAt)
                .build();

        ParameterValue changedValue = ParameterValue.builder()
                .id(2L)
                .gameObjectId(10L)
                .parameterId(101L)
                .value(10.0)
                .updatedAt(newUpdatedAt)
                .build();

        when(parameterValueRepository.findAllUpdatedSince(any()))
                .thenReturn(Flux.just(unchangedValue, changedValue));
        when(gameObjectRepository.findAllById(List.of(10L)))
                .thenReturn(Flux.just(new GameObject(10L, "player")));
        when(parameterRepository.findAllById(List.of(100L, 101L)))
                .thenReturn(Flux.just(
                        new Parameter(100L, "max_hp"),
                        new Parameter(101L, "attack_power")
                ));

        StepVerifier.create(dataService.getParameters(currentVersion))
                .assertNext(response -> {
                    assertResponseContainsAllChangedObjectParameters(response);
                    assert response.getVersion().equals("2024-01-02T12:00:00");
                })
                .verifyComplete();
    }

    private void assertResponseContainsAllChangedObjectParameters(ParametersResponse response) {
        assert response.getParameters().size() == 2;
        assert response.getParameters().stream().anyMatch(parameter ->
                parameter.getGameObjectName().equals("player")
                        && parameter.getParamName().equals("max_hp")
                        && parameter.getValue().equals(100.0));
        assert response.getParameters().stream().anyMatch(parameter ->
                parameter.getGameObjectName().equals("player")
                        && parameter.getParamName().equals("attack_power")
                        && parameter.getValue().equals(10.0));
    }
}
