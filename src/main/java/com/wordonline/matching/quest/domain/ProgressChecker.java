package com.wordonline.matching.quest.domain;

import reactor.core.publisher.Mono;

public interface ProgressChecker {

    Mono<Integer> check(long userId);
}
