package com.wordonline.matching.data.controller;

import com.wordonline.matching.data.dto.GameConfigResponse;
import com.wordonline.matching.data.dto.GameVersionResponse;
import com.wordonline.matching.data.dto.ParametersResponse;
import com.wordonline.matching.data.service.DataService;
import com.wordonline.matching.magic.dto.MagicsResponse;
import com.wordonline.matching.magic.service.MagicDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/data")
public class DataController {

    private final DataService dataService;
    private final MagicDataService magicDataService;

    @GetMapping("/parameters")
    public Mono<ParametersResponse> getParameters(@RequestParam(required = false) String currentVersion) {
        return dataService.getParameters(currentVersion);
    }

    @GetMapping("/magics")
    public Mono<MagicsResponse> getMagics(@RequestParam(required = false) String currentVersion) {
        return magicDataService.getMagics(currentVersion);
    }

    /**
     * Returns a version string representing the current config state.
     * Clients use this for a lightweight staleness check before downloading full config.
     */
    @GetMapping("/version")
    public Mono<GameVersionResponse> getVersion() {
        return magicDataService.getMagics(null)
                .map(magics -> new GameVersionResponse(magics.version()));
    }

    /**
     * Returns the full game config (magic recipes + balance parameters).
     * Matches the shape expected by the Unity client's GameDataManager.
     * Field name is magicRecipes (not magics) to match the client DTO.
     */
    @GetMapping("/config")
    public Mono<GameConfigResponse> getConfig() {
        Mono<MagicsResponse> magicsMono = magicDataService.getMagics(null);
        Mono<ParametersResponse> paramsMono = dataService.getParameters(null);

        return Mono.zip(magicsMono, paramsMono)
                .map(tuple -> new GameConfigResponse(
                        tuple.getT1().version(),
                        tuple.getT1().magics(),
                        tuple.getT2().getParameters()
                ));
    }
}
