package com.wordonline.matching.data.controller;

import com.wordonline.matching.data.domain.Parameter;
import com.wordonline.matching.data.dto.GameConfigResponse;
import com.wordonline.matching.data.dto.GameVersionResponse;
import com.wordonline.matching.data.dto.ParameterEntryDto;
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

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
     * Lightweight version check. Uses the max of magic version and parameter version
     * so clients re-fetch when either dataset changes.
     */
    @GetMapping("/version")
    public Mono<GameVersionResponse> getVersion() {
        Mono<MagicsResponse> magicsMono = magicDataService.getMagics(null);
        Mono<ParametersResponse> paramsMono = dataService.getParameters(null);

        return Mono.zip(magicsMono, paramsMono)
                .map(tuple -> {
                    String combined = combinedVersion(tuple.getT1().version(), tuple.getT2().getVersion());
                    return new GameVersionResponse(combined);
                });
    }

    /**
     * Full game config: magic recipes + balance parameters.
     * parameters is a list of {group, key, value} entries so multi-word game object
     * names (e.g. "fire_spirit") are unambiguous on the client side.
     */
    @GetMapping("/config")
    public Mono<GameConfigResponse> getConfig() {
        Mono<MagicsResponse> magicsMono = magicDataService.getMagics(null);
        Mono<ParametersResponse> paramsMono = dataService.getParameters(null);

        return Mono.zip(magicsMono, paramsMono)
                .map(tuple -> {
                    String version = combinedVersion(tuple.getT1().version(), tuple.getT2().getVersion());
                    List<ParameterEntryDto> entries = toEntryList(tuple.getT2().getParameters());
                    return new GameConfigResponse(version, tuple.getT1().magics(), entries);
                });
    }

    /** Returns the max of two ISO-datetime version strings (lexicographic comparison is valid for ISO 8601). */
    private static String combinedVersion(String v1, String v2) {
        return Stream.of(v1, v2)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse("0");
    }

    /** Converts List<Parameter> to list of {group, key, value} entries. */
    private static List<ParameterEntryDto> toEntryList(List<Parameter> parameters) {
        if (parameters == null) return List.of();
        return parameters.stream()
                .map(p -> new ParameterEntryDto(p.getGameObjectName(), p.getParamName(), p.getValue()))
                .collect(Collectors.toList());
    }
}
