package com.wordonline.matching.data.controller;

import com.wordonline.matching.data.dto.ParametersResponse;
import com.wordonline.matching.data.service.DataService;
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

    @GetMapping("/parameters")
    public Mono<ParametersResponse> getParameters(@RequestParam(required = false) String currentVersion) {
        return dataService.getParameters(currentVersion);
    }
}
