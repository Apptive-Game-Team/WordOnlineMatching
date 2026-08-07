package com.wordonline.matching.matching.service

import com.wordonline.matching.adventure.service.AdventureService
import com.wordonline.matching.auth.domain.UserStatus
import com.wordonline.matching.auth.service.UserService
import com.wordonline.matching.deck.service.DeckService
import com.wordonline.matching.matching.dto.SessionDto
import com.wordonline.matching.matching.repository.MatchingQueueRepository
import com.wordonline.matching.session.service.LegacyGameMatchService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import reactor.core.publisher.Mono

class GameMatchServiceTest {

    private val botMemberMaker = mock(BotMemberMaker::class.java)
    private val legacyGameMatchService = mock(LegacyGameMatchService::class.java)
    private val userService = mock(UserService::class.java)
    private val deckService = mock(DeckService::class.java)
    private val matchingQueueRepository = mock(MatchingQueueRepository::class.java)
    private val adventureService = mock(AdventureService::class.java)

    private lateinit var gameMatchService: GameMatchService

    @BeforeEach
    fun setUp() {
        gameMatchService = GameMatchService(
            botMemberMaker,
            legacyGameMatchService,
            userService,
            deckService,
            matchingQueueRepository,
            adventureService,
        )
    }

    @Test
    fun `rejects PVE play for a scenario that is not unlocked`() {
        `when`(deckService.hasValidSelectedDeck(1L)).thenReturn(Mono.just(true))
        `when`(userService.getStatus(1L)).thenReturn(Mono.just(UserStatus.Online))
        `when`(adventureService.isScenarioUnlocked(1L, 97L)).thenReturn(Mono.just(false))

        assertThrows<IllegalArgumentException> { runBlocking { gameMatchService.matchPVE(1L, 97L) } }
        verifyNoSessionCreated()
    }

    @Test
    fun `rejects practice session while the user is waiting in the matching queue`() {
        `when`(deckService.hasValidSelectedDeck(1L)).thenReturn(Mono.just(true))
        `when`(userService.getStatus(1L)).thenReturn(Mono.just(UserStatus.OnMatching))

        assertThrows<IllegalArgumentException> { runBlocking { gameMatchService.matchPractice(1L) } }
        verifyNoSessionCreated()
    }

    @Test
    fun `rejects practice session without a valid selected deck`() {
        `when`(deckService.hasValidSelectedDeck(1L)).thenReturn(Mono.just(false))

        assertThrows<IllegalStateException> { runBlocking { gameMatchService.matchPractice(1L) } }
        verifyNoSessionCreated()
    }

    @Test
    fun `reports cancellation failure when the user is no longer queued`() {
        `when`(matchingQueueRepository.remove(1L)).thenReturn(Mono.just(0L))

        assertFalse(runBlocking { gameMatchService.removeFromQueue(1L) })
        verify(userService, never()).markOnline(1L)
    }

    @Test
    fun `marks the user online when the queue entry is actually removed`() {
        `when`(matchingQueueRepository.remove(1L)).thenReturn(Mono.just(1L))
        `when`(userService.markOnline(1L)).thenReturn(Mono.empty())

        assertTrue(runBlocking { gameMatchService.removeFromQueue(1L) })
        verify(userService).markOnline(1L)
    }

    private fun verifyNoSessionCreated() {
        verify(legacyGameMatchService, never()).createSession(any(SessionDto::class.java))
    }
}
