package com.wordonline.matching.global.config

import com.wordonline.matching.matching.controller.InternalGameSessionController
import com.wordonline.matching.matching.service.SessionEndedNotificationService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.web.server.WebFilterChainProxy
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import java.time.Instant

/**
 * The one thing this endpoint must never allow: a player ending someone else's game session
 * with the token they already have. A valid signature is not enough - the token has to be a
 * shape only the account server's server-token endpoint produces.
 */
class InternalApiSecurityConfigTest {

    private val service: SessionEndedNotificationService = mock()
    private val issuedAt: Instant = Instant.parse("2026-08-10T00:00:00Z")

    private val serverToken = jwt("service-token") {
        claim("type", "server_token")
        claim("scope", "WORDONLINE_GAME_SERVER")
        subject("server")
    }

    /** A server token minted for some other internal service, without the game-server authority. */
    private val otherServiceToken = jwt("other-service-token") {
        claim("type", "server_token")
        claim("scope", "WORDONLINE_ADMIN")
        subject("server")
    }

    private val userToken = jwt("user-token") {
        claim("memberId", 42L)
        claim("scope", "WORDONLINE_USER")
        subject("player@team6515.com")
    }

    /** A user who managed to get the claim onto a token issued for them still carries `memberId`. */
    private val forgedUserToken = jwt("forged-user-token") {
        claim("type", "server_token")
        claim("memberId", 42L)
        claim("scope", "WORDONLINE_GAME_SERVER")
        subject("player@team6515.com")
    }

    private val tokens = listOf(serverToken, otherServiceToken, userToken, forgedUserToken)
        .associateBy { it.tokenValue }

    private val decoder = ReactiveJwtDecoder { token ->
        tokens[token]?.let { Mono.just(it) } ?: Mono.error(BadJwtException("unknown token"))
    }

    @Test
    fun `서비스 토큰이면 통과한다`() {
        client().post().uri(URI).header(HttpHeaders.AUTHORIZATION, "Bearer ${serverToken.tokenValue}")
            .contentType(MediaType.APPLICATION_JSON).bodyValue(BODY)
            .exchange()
            .expectStatus().isNoContent
    }

    @Test
    fun `토큰이 없으면 401이다`() {
        client().post().uri(URI)
            .contentType(MediaType.APPLICATION_JSON).bodyValue(BODY)
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `사용자 토큰으로는 호출할 수 없다`() {
        client().post().uri(URI).header(HttpHeaders.AUTHORIZATION, "Bearer ${userToken.tokenValue}")
            .contentType(MediaType.APPLICATION_JSON).bodyValue(BODY)
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `사용자 토큰에 서버 토큰 표식이 붙어 있어도 거부한다`() {
        client().post().uri(URI).header(HttpHeaders.AUTHORIZATION, "Bearer ${forgedUserToken.tokenValue}")
            .contentType(MediaType.APPLICATION_JSON).bodyValue(BODY)
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `서명이 확인되지 않는 토큰은 401이다`() {
        client().post().uri(URI).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token")
            .contentType(MediaType.APPLICATION_JSON).bodyValue(BODY)
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `권한을 지정하면 그 권한이 없는 서버 토큰은 거부한다`() {
        val restricted = client(requiredAuthority = "WORDONLINE_GAME_SERVER")

        restricted.post().uri(URI).header(HttpHeaders.AUTHORIZATION, "Bearer ${otherServiceToken.tokenValue}")
            .contentType(MediaType.APPLICATION_JSON).bodyValue(BODY)
            .exchange()
            .expectStatus().isForbidden

        restricted.post().uri(URI).header(HttpHeaders.AUTHORIZATION, "Bearer ${serverToken.tokenValue}")
            .contentType(MediaType.APPLICATION_JSON).bodyValue(BODY)
            .exchange()
            .expectStatus().isNoContent
    }

    @Test
    fun `권한을 지정하지 않으면 다른 내부 서비스의 서버 토큰도 허용한다`() {
        client().post().uri(URI).header(HttpHeaders.AUTHORIZATION, "Bearer ${otherServiceToken.tokenValue}")
            .contentType(MediaType.APPLICATION_JSON).bodyValue(BODY)
            .exchange()
            .expectStatus().isNoContent
    }

    private fun client(requiredAuthority: String = ""): WebTestClient {
        val chain = InternalApiSecurityConfig(InternalApiProperties(requiredAuthority))
            .internalApiSecurityFilterChain(ServerHttpSecurity.http(), decoder)
        return WebTestClient.bindToController(InternalGameSessionController(service))
            .webFilter<WebTestClient.ControllerSpec>(WebFilterChainProxy(chain))
            .build()
    }

    private fun jwt(tokenValue: String, claims: Jwt.Builder.() -> Unit): Jwt =
        Jwt.withTokenValue(tokenValue)
            .header("alg", "RS256")
            .issuer("self")
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plusSeconds(3600))
            .apply(claims)
            .build()

    private companion object {
        const val URI = "/api/internal/game-sessions/session-1/ended"
        const val BODY = """{"instanceId":"boot-1"}"""
    }
}
