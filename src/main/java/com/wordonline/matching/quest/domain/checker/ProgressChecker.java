package com.wordonline.matching.quest.domain.checker;

import reactor.core.publisher.Mono;

public interface ProgressChecker {

    Mono<Integer> check(long userId);
}
