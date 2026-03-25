package com.wordonline.matching.global.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping
@RestController
public class HealthCheckController {
    @GetMapping("/healthcheck")
    public String healthcheck() {
        return "OK";
    }
}
