package com.wordonline.matching.matching.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "matching")
class MatchingProperties {

    /** Tickets pulled per matching tick. Bounds tick duration when the queue is long. */
    var batchSize: Int = 400

    /** MMR gap tolerated right after enqueueing. */
    var baseMmrDiff: Long = 100

    /** How much the tolerated MMR gap grows per second of waiting. */
    var widenPerSec: Long = 20

    /** After this wait, MMR stops mattering. Prevents starvation. */
    var maxWaitSeconds: Long = 60

    /** Queued tickets older than this are dropped. */
    var queueTimeoutSeconds: Int = 300

    /** Session creation attempts before a ticket is given up on. */
    var maxRetry: Int = 3

    /** MATCHED tickets stuck this long are put back in the queue. */
    var stuckMatchedSeconds: Int = 30

    /** PLAYING tickets younger than this are never reconciled away. */
    var reconcileGraceSeconds: Int = 30

    /** Upper bound a client may request for long-poll status. */
    var maxStatusWaitSeconds: Long = 30

    /**
     * How often a waiting long-poll re-reads its own ticket.
     * Covers state changes made by another lobby instance, whose in-process notifications
     * this instance never sees.
     */
    var statusRecheckMillis: Long = 2000
}
