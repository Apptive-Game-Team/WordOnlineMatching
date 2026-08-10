package com.wordonline.matching.session.service

import com.wordonline.matching.auth.dto.UserDetailResponseDto
import com.wordonline.matching.auth.service.UserService
import com.wordonline.matching.global.service.LocalizationService
import com.wordonline.matching.matching.dto.SessionDto
import com.wordonline.matching.server.entity.Server
import com.wordonline.matching.server.entity.ServerState
import com.wordonline.matching.server.entity.ServerType
import com.wordonline.matching.server.exception.GameServerUnreachableException
import com.wordonline.matching.server.exception.NoAvailableGameServerException
import com.wordonline.matching.server.service.GameServerManagementService
import com.wordonline.matching.session.domain.SessionRecoveryInfo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

class LegacyGameMatchServiceTest {

    private val gameServerManagementService: GameServerManagementService = mock()
    private val sessionRecoveryStore: SessionRecoveryStore = mock()
    private val localizationService: LocalizationService = mock()
    private val userService: UserService = mock()

    /** Every request the service actually sent, so tests can assert which host was asked. */
    private val sentRequests = mutableListOf<ClientRequest>()

    private fun service(respond: (ClientRequest) -> ClientResponse): LegacyGameMatchService {
        val builder = WebClient.builder().exchangeFunction { request ->
            sentRequests += request
            Mono.just(respond(request))
        }
        return LegacyGameMatchService(
            builder,
            sessionRecoveryStore,
            localizationService,
            userService,
            gameServerManagementService,
        )
    }

    private fun readyResponse(ready: Boolean): ClientResponse =
        ClientResponse.create(HttpStatus.OK)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .body("""{"attemptId":"attempt-1","sessionId":"session-1","ready":$ready,"serverUrl":"http://internal:9090","webSocketUrl":"wss://game.example/ws"}""")
            .build()

    private fun server(id: Long, domain: String) = Server(
        id = id,
        protocol = "http",
        domain = domain,
        port = 9090,
        state = ServerState.ACTIVE,
        type = ServerType.GAME,
    )

    private fun stubUsers() {
        whenever(userService.getUserDetail(any()))
            .thenAnswer { Mono.just(UserDetailResponseDto(it.getArgument(0), "user", "user@team6515.com")) }
        whenever(sessionRecoveryStore.storeMatchInfo(any())).thenReturn(Mono.empty())
        whenever(localizationService.getMessage(any(), any())).thenAnswer { it.getArgument(1) }
    }

    private val sessionDto = SessionDto.PVP("session-1", 1L, 2L)

    @Test
    fun `첫 서버가 거절하면 다음 서버로 넘어가고 수락한 서버의 URL을 반환한다`() = runTest {
        stubUsers()
        whenever(gameServerManagementService.getAvailableServers())
            .thenReturn(listOf(server(1L, "alpha"), server(2L, "beta")))

        val matched = service { request ->
            readyResponse(request.url().host == "beta")
        }.createSession(sessionDto, "attempt-1")

        assertThat(matched.server)
            .`as`("첫 후보가 아니라 실제로 수락한 서버의 URL이어야 한다")
            .isEqualTo("http://internal:9090")
        assertThat(matched.webSocketUrl).isEqualTo("wss://game.example/ws")
        assertThat(sentRequests.map { it.url().host }).containsExactly("alpha", "beta")
    }

    @Test
    fun `통신 실패도 페일오버 대상이다`() = runTest {
        stubUsers()
        whenever(gameServerManagementService.getAvailableServers())
            .thenReturn(listOf(server(1L, "alpha"), server(2L, "beta")))

        val builder = WebClient.builder().exchangeFunction { request ->
            sentRequests += request
            if (request.url().host == "alpha") Mono.error(IllegalStateException("connection refused"))
            else Mono.just(readyResponse(true))
        }
        val service = LegacyGameMatchService(
            builder, sessionRecoveryStore, localizationService, userService, gameServerManagementService,
        )

        val matched = service.createSession(sessionDto, "attempt-1")
        assertThat(matched.server).isEqualTo("http://internal:9090")
        assertThat(matched.webSocketUrl).isEqualTo("wss://game.example/ws")
    }

    @Test
    fun `가용 서버가 없으면 NoAvailableGameServerException을 던진다`() = runTest {
        stubUsers()
        whenever(gameServerManagementService.getAvailableServers()).thenReturn(emptyList())

        val error = runCatching { service { readyResponse(true) }.createSession(sessionDto, "attempt-1") }.exceptionOrNull()

        assertThat(error).isInstanceOf(NoAvailableGameServerException::class.java)
        assertThat(sentRequests).`as`("서버가 없으면 요청을 보내지 않는다").isEmpty()
    }

    @Test
    fun `모든 후보가 거절하면 NoAvailableGameServerException을 던진다`() = runTest {
        stubUsers()
        whenever(gameServerManagementService.getAvailableServers())
            .thenReturn(listOf(server(1L, "alpha"), server(2L, "beta")))

        val error = runCatching { service { readyResponse(false) }.createSession(sessionDto, "attempt-1") }.exceptionOrNull()

        assertThat(error).isInstanceOf(NoAvailableGameServerException::class.java)
        assertThat(sentRequests.map { it.url().host })
            .`as`("포기하기 전에 모든 후보를 시도한다").containsExactly("alpha", "beta")
    }

    @Test
    fun `유저 조회에 실패하면 게임 서버에 방을 만들지 않는다`() = runTest {
        whenever(gameServerManagementService.getAvailableServers())
            .thenReturn(listOf(server(1L, "alpha"), server(2L, "beta")))
        whenever(userService.getUserDetail(1L))
            .thenReturn(Mono.error(IllegalStateException("account server down")))
        whenever(userService.getUserDetail(2L))
            .thenReturn(Mono.just(UserDetailResponseDto(2L, "right", "right@team6515.com")))

        val error = runCatching { service { readyResponse(true) }.createSession(sessionDto, "attempt-1") }
            .exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalStateException::class.java)
        assertThat(sentRequests)
            .`as`("유저 조회가 실패하면 어떤 게임 서버에도 방 생성 요청을 보내지 않는다")
            .isEmpty()
    }

    @Test
    fun `다른 attempt 응답은 준비 완료로 인정하지 않는다`() = runTest {
        stubUsers()
        whenever(gameServerManagementService.getAvailableServers()).thenReturn(listOf(server(1L, "alpha")))
        val mismatched = ClientResponse.create(HttpStatus.OK)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .body("""{"attemptId":"stale-attempt","sessionId":"session-1","ready":true,"serverUrl":"http://alpha:9090","webSocketUrl":"wss://alpha/ws"}""")
            .build()

        val error = runCatching { service { mismatched }.createSession(sessionDto, "attempt-1") }.exceptionOrNull()

        assertThat(error).isInstanceOf(NoAvailableGameServerException::class.java)
    }

    @Test
    fun `getMatchInfo는 세션을 호스팅하는 서버에 묻는다`() = runTest {
        stubUsers()
        whenever(sessionRecoveryStore.getSessionInfo(1L)).thenReturn(
            Mono.just(SessionRecoveryInfo(1L, 2L, "session-1", "http://gamma:9090", Long.MAX_VALUE)),
        )
        // a different server is available; asking it instead is the bug under test
        whenever(gameServerManagementService.getAvailableServers()).thenReturn(listOf(server(1L, "alpha")))

        val matched = service { booleanResponse(true) }.getMatchInfo(1L)

        assertThat(sentRequests).hasSize(1)
        assertThat(sentRequests.single().url().host).isEqualTo("gamma")
        assertThat(matched.sessionId).isEqualTo("session-1")
    }

    private fun booleanResponse(value: Boolean): ClientResponse =
        ClientResponse.create(HttpStatus.OK)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .body("""{"value":$value}""")
            .build()

    @Test
    fun `호스팅 서버가 응답하지 않으면 세션 종료가 아니라 도달 불가로 구분한다`() = runTest {
        stubUsers()
        whenever(sessionRecoveryStore.getSessionInfo(1L)).thenReturn(
            Mono.just(SessionRecoveryInfo(1L, 2L, "session-1", "http://gamma:9090", Long.MAX_VALUE)),
        )

        val builder = WebClient.builder().exchangeFunction { Mono.error(IllegalStateException("unreachable")) }
        val service = LegacyGameMatchService(
            builder, sessionRecoveryStore, localizationService, userService, gameServerManagementService,
        )

        val error = runCatching { service.getMatchInfo(1L) }.exceptionOrNull()

        assertThat(error).isInstanceOf(GameServerUnreachableException::class.java)
    }
}
