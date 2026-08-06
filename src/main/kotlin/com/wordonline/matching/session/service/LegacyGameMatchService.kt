package com.wordonline.matching.session.service

import com.wordonline.matching.auth.dto.UserDetailResponseDto
import com.wordonline.matching.auth.service.UserService
import com.wordonline.matching.global.service.LocalizationService
import com.wordonline.matching.matching.dto.MatchedInfoDto
import com.wordonline.matching.matching.dto.SessionDto
import com.wordonline.matching.server.entity.Server
import com.wordonline.matching.server.exception.GameServerUnreachableException
import com.wordonline.matching.server.exception.NoAvailableGameServerException
import com.wordonline.matching.server.service.GameServerManagementService
import com.wordonline.matching.session.domain.SessionRecoveryInfo
import com.wordonline.matching.session.dto.SimpleBooleanDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.reactor.ReactorContext
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.context.i18n.LocaleContext
import org.springframework.context.i18n.SimpleLocaleContext
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.util.Locale

@Service
class LegacyGameMatchService(
    private val webClientBuilder: WebClient.Builder,
    private val sessionRecoveryStore: SessionRecoveryStore,
    private val localizationService: LocalizationService,
    private val userService: UserService,
    private val gameServerManagementService: GameServerManagementService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Places a session on the first game server that accepts it.
     *
     * Candidates are tried in order and a refusal is not fatal: a game server that has
     * already flipped itself out of `ACTIVE` answers `false`, and a redeploying one drops
     * the connection. Both used to fail the whole match even when another healthy server
     * was sitting right there.
     */
    suspend fun createSession(sessionDto: SessionDto): MatchedInfoDto {
        val candidates = gameServerManagementService.getAvailableServers()
        if (candidates.isEmpty()) {
            throw NoAvailableGameServerException(localizedMessage("error.gameserver.unavailable"))
        }

        for (server in candidates) {
            if (!offerSession(server, sessionDto)) continue

            log.info("Session created on game server {}: sessionId={}", server.url, sessionDto.sessionId)
            val (leftUser, rightUser) = userDetails(sessionDto.uid1, sessionDto.uid2)
            // the URL must come from the server that actually accepted, not from the first candidate
            val matchedInfo = MatchedInfoDto(
                "Successfully Matched",
                server.url,
                leftUser,
                rightUser,
                sessionDto.sessionId,
            )
            sessionRecoveryStore.storeMatchInfo(matchedInfo).awaitSingleOrNull()
            return matchedInfo
        }

        log.error(
            "Every game server refused the session: sessionId={}, candidates={}",
            sessionDto.sessionId,
            candidates.size,
        )
        throw NoAvailableGameServerException(localizedMessage("error.gameserver.unavailable"))
    }

    /** @return true when this server took the session; false when it refused or failed. */
    private suspend fun offerSession(server: Server, sessionDto: SessionDto): Boolean =
        try {
            val accepted = webClientBuilder.baseUrl(server.url).build()
                .post()
                .uri("/api/server/game-sessions")
                .body(Mono.just(sessionDto), SessionDto::class.java)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(SimpleBooleanDto::class.java)
                .awaitSingle()
                .value()

            if (!accepted) {
                log.warn("Game server {} refused session {}", server.url, sessionDto.sessionId)
            }
            accepted
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Game server {} failed to take session {}: {}", server.url, sessionDto.sessionId, e.toString())
            false
        }

    suspend fun getMatchInfo(userId: Long): MatchedInfoDto {
        val sessionInfo = sessionRecoveryStore.getSessionInfo(userId).awaitSingleOrNull()
            ?: throw IllegalArgumentException("Session Not Found")

        // ask the server that hosts this session, not an arbitrary available one
        if (!checkSessionActive(sessionInfo.serverUrl(), sessionInfo.sessionId())) {
            throw IllegalArgumentException("Session Already Deactivated")
        }

        val (leftUser, rightUser) = userDetails(sessionInfo.leftUserId(), sessionInfo.rightUserId())
        return MatchedInfoDto(sessionInfo, leftUser, rightUser)
    }

    /**
     * A host that does not answer is a transient failure, not proof the session ended, so
     * this throws [GameServerUnreachableException] instead of reporting the session gone.
     */
    private suspend fun checkSessionActive(serverUrl: String, sessionId: String): Boolean =
        try {
            webClientBuilder.baseUrl(serverUrl).build()
                .get()
                .uri("/api/server/game-sessions/$sessionId/active")
                .retrieve()
                .bodyToMono(SimpleBooleanDto::class.java)
                .awaitSingle()
                .value()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Host {} did not answer for session {}: {}", serverUrl, sessionId, e.toString())
            throw GameServerUnreachableException(localizedMessage("error.gameserver.unreachable"), e)
        }

    private suspend fun userDetails(
        userId1: Long,
        userId2: Long?,
    ): Pair<UserDetailResponseDto, UserDetailResponseDto> = coroutineScope {
        val left = async { userService.getUserDetail(userId1).awaitSingle() }
        val right = async {
            if (userId2 != null) userService.getUserDetail(userId2).awaitSingle()
            else UserDetailResponseDto(0L, "dummy", "dummy@team6515.com")
        }
        left.await() to right.await()
    }

    /**
     * The request locale rides in the Reactor context (see `LocaleContextWebFilter`), which
     * Spring propagates into suspending handlers. The scheduled matching loop has no request
     * behind it, so the absent context falls back to the default locale instead of throwing -
     * the previous `ctx.get(LocaleContext.class)` would have blown up on that path.
     */
    private suspend fun localizedMessage(code: String): String {
        val localeContext: LocaleContext = currentCoroutineContext()[ReactorContext]
            ?.context
            ?.getOrEmpty<LocaleContext>(LocaleContext::class.java)
            ?.orElse(null)
            ?: SimpleLocaleContext(Locale.getDefault())
        return localizationService.getMessage(localeContext, code)
    }
}
