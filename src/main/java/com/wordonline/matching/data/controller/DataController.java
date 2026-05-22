package com.wordonline.matching.data.controller;

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
}
