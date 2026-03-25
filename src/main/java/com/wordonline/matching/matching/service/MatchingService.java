package com.wordonline.matching.matching.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchingService {

    public String getHealthLog() {
        return "Matching Service is running";
    }
}
