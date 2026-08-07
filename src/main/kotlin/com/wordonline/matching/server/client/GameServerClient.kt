package com.wordonline.matching.server.client

import com.wordonline.matching.server.config.GameServerProperties
import com.wordonline.matching.server.dto.RoomListDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@Component
class GameServerClient(
    private val webClientBuilder: WebClient.Builder,
    private val properties: GameServerProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Java interop boundary: `UserService` and `GameSessionService` are still Reactor-based
     * Java, so this one keeps returning [Mono]. Convert alongside those callers.
     */
    fun getGameSessions(serverUrl: String): Mono<RoomListDto> {
        log.info("Fetching game sessions from server: {}", serverUrl)

        return webClientBuilder.baseUrl(serverUrl).build()
            .get()
            .uri("/api/server/game-sessions")
            .retrieve()
            .bodyToMono(RoomListDto::class.java)
            // The timeout stays *above* onErrorResume so an unresponsive server collapses to an
            // empty room list. Below it, the TimeoutException would escape the error handler.
            .timeout(properties.sessionsTimeout)
            .onErrorResume { error ->
                log.error("Failed to fetch game sessions from server: {}", serverUrl, error)
                Mono.just(RoomListDto(emptyList()))
            }
    }

    /**
     * Probes the game server health endpoint.
     *
     * Returns `true` only when the server answered 2xx in time. Every failure mode -
     * timeout, connection refused, non-2xx - collapses to `false`; this never throws.
     * That matters because the caller probes many servers concurrently and one bad
     * server must not abort the others.
     *
     * The timeout is expressed with [withTimeoutOrNull] rather than a Reactor `timeout`
     * operator, which makes the original bug (a timeout signalled *below* the error
     * handler and therefore escaping it) structurally impossible.
     */
    suspend fun healthcheck(serverUrl: String): Boolean {
        val healthy = try {
            // withTimeoutOrNull takes millis or a kotlin.time.Duration, not java.time.Duration.
            withTimeoutOrNull(properties.healthcheckTimeout.toMillis()) {
                webClientBuilder.baseUrl(serverUrl).build()
                    .get()
                    .uri("/healthcheck")
                    .retrieve()
                    .toBodilessEntity()
                    .awaitSingle()
                    .statusCode
                    .is2xxSuccessful
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Healthcheck failed for server: {} ({})", serverUrl, e.toString())
            return false
        }

        if (healthy == null) {
            log.warn("Healthcheck timed out after {} for server: {}", properties.healthcheckTimeout, serverUrl)
            return false
        }
        return healthy
    }
}
