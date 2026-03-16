package com.wordonline.matching.data.controller;

import com.wordonline.matching.data.service.DataService;
import com.wordonline.matching.magic.dto.MagicDto;
import com.wordonline.matching.magic.dto.MagicsResponse;
import com.wordonline.matching.magic.service.MagicDataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(DataController.class)
class DataControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private DataService dataService;

    @MockitoBean
    private MagicDataService magicDataService;

    @Test
    @DisplayName("마법_전체_조회_버전없음_성공")
    void getMagics_WithoutVersion_ReturnsAllMagics() {
        MagicDto magicDto = new MagicDto(1L, "fireball", List.of("Fire", "Fire", "Shoot"));
        MagicsResponse mockResponse = new MagicsResponse("2024-01-01T00:00:00", List.of(magicDto));

        when(magicDataService.getMagics(isNull())).thenReturn(Mono.just(mockResponse));

        webTestClient
                .get()
                .uri("/api/data/magics")
                .exchange()
                .expectStatus().isOk()
                .expectBody(MagicsResponse.class)
                .value(response -> {
                    assert response.version().equals("2024-01-01T00:00:00");
                    assert response.magics().size() == 1;
                    assert response.magics().get(0).id().equals(1L);
                    assert response.magics().get(0).name().equals("fireball");
                    assert response.magics().get(0).cards().equals(List.of("Fire", "Fire", "Shoot"));
                });
    }

    @Test
    @DisplayName("마법_변경사항_조회_버전있음_성공")
    void getMagics_WithVersion_ReturnsUpdatedMagics() {
        String currentVersion = "2024-01-01T00:00:00";
        MagicsResponse mockResponse = new MagicsResponse(currentVersion, List.of());

        when(magicDataService.getMagics(eq(currentVersion))).thenReturn(Mono.just(mockResponse));

        webTestClient
                .get()
                .uri("/api/data/magics?currentVersion=" + currentVersion)
                .exchange()
                .expectStatus().isOk()
                .expectBody(MagicsResponse.class)
                .value(response -> {
                    assert response.version().equals(currentVersion);
                    assert response.magics().isEmpty();
                });
    }
}
