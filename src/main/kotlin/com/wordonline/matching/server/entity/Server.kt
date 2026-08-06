package com.wordonline.matching.server.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

@Table(name = "servers")
data class Server(
    @Id val id: Long? = null,
    val protocol: String? = null,
    val domain: String? = null,
    val port: Int? = null,
    val state: ServerState? = null,
    val type: ServerType? = null,
) {
    val isLocal: Boolean
        get() = domain == "localhost" || domain == "127.0.0.1"

    val url: String
        get() {
            check(protocol != null && domain != null && port != null) {
                "Server protocol, domain, and port must not be null"
            }
            return "$protocol://$domain:$port"
        }

    /**
     * A draining server finishes its running sessions but must never receive a new one.
     *
     * Every other persisted [ServerState] is only a hint: live availability is decided by
     * the health check result held in
     * [com.wordonline.matching.server.service.ServerHealthRegistry], not by this column.
     */
    val isDraining: Boolean
        get() = state == ServerState.DRAINING
}
