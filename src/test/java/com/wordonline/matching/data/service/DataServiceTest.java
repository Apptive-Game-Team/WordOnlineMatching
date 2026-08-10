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
    @DisplayName("버전_조회시_변경이_있으면_전체_파라미터를_반환")
    void getParameters_WithVersion_ReturnsAllParametersWhenAnyChangeExists() {
        String currentVersion = "2024-01-01T00:00:00";
        LocalDateTime newUpdatedAt = LocalDateTime.parse("2024-01-02T12:00:00");

        ParameterValue changedValue = ParameterValue.builder()
                .id(2L)
                .gameObjectId(10L)
                .parameterId(101L)
                .value(10.0)
                .updatedAt(newUpdatedAt)
                .build();

        ParameterValue fullPlayerMaxHp = ParameterValue.builder()
                .id(1L)
                .gameObjectId(10L)
                .parameterId(100L)
                .value(100.0)
                .updatedAt(LocalDateTime.parse("2024-01-01T00:00:00"))
                .build();

        ParameterValue fullPlayerAttackPower = ParameterValue.builder()
                .id(2L)
                .gameObjectId(10L)
                .parameterId(101L)
                .value(10.0)
                .updatedAt(newUpdatedAt)
                .build();

        ParameterValue fullEnemyHp = ParameterValue.builder()
                .id(3L)
                .gameObjectId(20L)
                .parameterId(100L)
                .value(300.0)
                .updatedAt(LocalDateTime.parse("2024-01-01T08:00:00"))
                .build();

        when(parameterValueRepository.findAllUpdatedSince(any()))
                .thenReturn(Flux.just(changedValue));
        when(parameterValueRepository.findAllParameters())
                .thenReturn(Flux.just(fullPlayerMaxHp, fullPlayerAttackPower, fullEnemyHp));
        when(gameObjectRepository.findAllById(List.of(10L, 20L)))
                .thenReturn(Flux.just(
                        new GameObject(10L, "player"),
                        new GameObject(20L, "enemy")
                ));
        when(parameterRepository.findAllById(List.of(100L, 101L)))
                .thenReturn(Flux.just(
                        new Parameter(100L, "max_hp"),
                        new Parameter(101L, "attack_power")
                ));

        StepVerifier.create(dataService.getParameters(currentVersion))
                .assertNext(response -> {
                    assertResponseContainsFullParameterSnapshot(response);
                    assert response.getVersion().equals("2024-01-02T12:00:00");
                    assert response.isRequiresRefresh();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("버전없이_조회시_전체_스냅샷과_changed_true를_반환")
    void getParameters_WithoutVersion_ReturnsFullSnapshotAndChangedTrue() {
        LocalDateTime updatedAt = LocalDateTime.parse("2024-01-02T12:00:00");
        ParameterValue parameterValue = ParameterValue.builder()
                .id(1L)
                .gameObjectId(10L)
                .parameterId(100L)
                .value(100.0)
                .updatedAt(updatedAt)
                .build();

        when(parameterValueRepository.findAllParameters())
                .thenReturn(Flux.just(parameterValue));
        when(gameObjectRepository.findAllById(List.of(10L)))
                .thenReturn(Flux.just(new GameObject(10L, "player")));
        when(parameterRepository.findAllById(List.of(100L)))
                .thenReturn(Flux.just(new Parameter(100L, "max_hp")));

        StepVerifier.create(dataService.getParameters(null))
                .assertNext(response -> {
                    assert response.getParameters().size() == 1;
                    assert response.getVersion().equals("2024-01-02T12:00:00");
                    assert response.isRequiresRefresh();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("버전_조회시_변경이_없으면_빈_응답과_기존_버전을_반환")
    void getParameters_WithVersion_ReturnsEmptyWhenNothingChanged() {
        String currentVersion = "2024-01-01T00:00:00";

        when(parameterValueRepository.findAllUpdatedSince(any()))
                .thenReturn(Flux.empty());

        StepVerifier.create(dataService.getParameters(currentVersion))
                .assertNext(response -> {
                    assert response.getParameters().isEmpty();
                    assert response.getVersion().equals(currentVersion);
                    assert !response.isRequiresRefresh();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("값이_null인_파라미터는_제외하되_버전은_전체_기준으로_계산")
    void getParameters_ExcludesNullValuesButKeepsVersionOverAllRows() {
        ParameterValue validValue = ParameterValue.builder()
                .id(1L)
                .gameObjectId(10L)
                .parameterId(100L)
                .value(100.0)
                .updatedAt(LocalDateTime.parse("2024-01-01T00:00:00"))
                .build();

        ParameterValue nullValue = ParameterValue.builder()
                .id(2L)
                .gameObjectId(10L)
                .parameterId(101L)
                .value(null)
                .updatedAt(LocalDateTime.parse("2024-01-03T09:00:00"))
                .build();

        when(parameterValueRepository.findAllParameters())
                .thenReturn(Flux.just(validValue, nullValue));
        when(gameObjectRepository.findAllById(List.of(10L)))
                .thenReturn(Flux.just(new GameObject(10L, "player")));
        when(parameterRepository.findAllById(List.of(100L)))
                .thenReturn(Flux.just(new Parameter(100L, "max_hp")));

        StepVerifier.create(dataService.getParameters(null))
                .assertNext(response -> {
                    assert response.getParameters().size() == 1;
                    assert response.getParameters().stream().noneMatch(parameter -> parameter.getValue() == null);
                    assert response.getParameters().get(0).getGameObjectName().equals("player");
                    assert response.getParameters().get(0).getParamName().equals("max_hp");
                    assert response.getParameters().get(0).getValue().equals(100.0);
                    assert response.getVersion().equals("2024-01-03T09:00:00");
                    assert response.isRequiresRefresh();
                })
                .verifyComplete();
    }

    private void assertResponseContainsFullParameterSnapshot(ParametersResponse response) {
        assert response.getParameters().size() == 3;
        assert response.getParameters().stream().anyMatch(parameter ->
                parameter.getGameObjectName().equals("player")
                        && parameter.getParamName().equals("max_hp")
                        && parameter.getValue().equals(100.0));
        assert response.getParameters().stream().anyMatch(parameter ->
                parameter.getGameObjectName().equals("player")
                        && parameter.getParamName().equals("attack_power")
                        && parameter.getValue().equals(10.0));
        assert response.getParameters().stream().anyMatch(parameter ->
                parameter.getGameObjectName().equals("enemy")
                        && parameter.getParamName().equals("max_hp")
                        && parameter.getValue().equals(300.0));
    }
}
