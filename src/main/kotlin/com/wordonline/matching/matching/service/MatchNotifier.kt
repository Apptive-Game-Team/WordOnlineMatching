package com.wordonline.matching.matching.service

import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks

/**
 * Wakes up long-polling status requests as soon as this instance changes a ticket.
 * Best effort only: a request that misses the signal still gets the change from its own
 * periodic re-read, which is also what covers changes made by other lobby instances.
 */
@Component
class MatchNotifier {

    private val sink = Sinks.many().multicast().directBestEffort<Long>()

    fun changed(userId: Long) {
        sink.tryEmitNext(userId)
    }

    fun changesOf(userId: Long): Flux<Long> = sink.asFlux().filter { it == userId }
}
