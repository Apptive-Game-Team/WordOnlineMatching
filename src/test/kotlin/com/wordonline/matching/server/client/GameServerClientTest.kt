package com.wordonline.matching.server.client

import com.wordonline.matching.server.config.GameServerProperties
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.Duration
import kotlin.system.measureTimeMillis

/**
 * Uses [runBlocking] rather than `runTest`: these exercise the real Reactor scheduling of
 * `WebClient`, and the virtual clock of `runTest` would skip the very delays under test.
 */
class GameServerClientTest {

    private val serverUrl = "http://alpha:9090"

    private fun client(
        timeout: Duration,
        sessionsTimeout: Duration = Duration.ofSeconds(3),
        exchange: ExchangeFunction,
    ): GameServerClient =
        GameServerClient(
            WebClient.builder().exchangeFunction(exchange),
            GameServerProperties(healthcheckTimeout = timeout, sessionsTimeout = sessionsTimeout),
        )

    @Test
    fun `2xx로 응답하면 true를 반환한다`() = runBlocking<Unit> {
        val gameServerClient = client(Duration.ofSeconds(2)) {
            Mono.just(ClientResponse.create(HttpStatus.OK).build())
        }

        assertThat(gameServerClient.healthcheck(serverUrl)).isTrue()
    }

    @Test
    fun `5xx로 응답하면 false를 반환한다`() = runBlocking<Unit> {
        val gameServerClient = client(Duration.ofSeconds(2)) {
            Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build())
        }

        assertThat(gameServerClient.healthcheck(serverUrl)).isFalse()
    }

    @Test
    fun `응답이 타임아웃보다 느리면 예외를 던지지 않고 false를 반환한다`() = runBlocking<Unit> {
        // the original bug: the timeout was signalled below the error handler, so it
        // escaped healthcheck() and killed the caller's whole health sweep.
        val gameServerClient = client(Duration.ofMillis(50)) {
            Mono.just(ClientResponse.create(HttpStatus.OK).build())
                .delayElement(Duration.ofSeconds(2))
        }

        assertThat(gameServerClient.healthcheck(serverUrl)).isFalse()
    }

    @Test
    fun `연결 오류면 false를 반환한다`() = runBlocking<Unit> {
        val gameServerClient = client(Duration.ofSeconds(2)) {
            Mono.error(IllegalStateException("connection refused"))
        }

        assertThat(gameServerClient.healthcheck(serverUrl)).isFalse()
    }

    @Test
    fun `응답하지 않는 게임 서버는 타임아웃 후 빈 방 목록을 반환한다`() = runBlocking<Unit> {
        // The slow response carries a NON-empty room list on purpose: only the timeout can
        // turn it into an empty one. An empty-bodied response would pass with or without the
        // timeout and would not detect its removal.
        val gameServerClient = client(Duration.ofSeconds(2), sessionsTimeout = Duration.ofMillis(50)) {
            Mono.just(
                ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body("{\"rooms\":[{\"sessionId\":\"s-1\",\"leftUserId\":1,\"rightUserId\":2,\"serverUrl\":\"http://alpha:9090\"}]}")
                    .build(),
            ).delayElement(Duration.ofSeconds(2))
        }

        val elapsed = measureTimeMillis {
            assertThat(gameServerClient.getGameSessions(serverUrl).awaitSingle().rooms).isEmpty()
        }
        assertThat(elapsed).isLessThan(1_000)
    }
}
