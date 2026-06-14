package at.aau.hexabrawl.websocketserver.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.*
import org.springframework.messaging.simp.SimpMessagingTemplate
import at.aau.hexabrawl.websocketserver.TestServiceFactory

/**
 * Tests fuer den DisconnectCleanupService.
 *
 * Ruft cleanupExpired() direkt auf — der Spring-Scheduler ist Sache der Integration-Tests.
 */
class DisconnectCleanupServiceTest {

    private lateinit var gameService: GameService
    private lateinit var roomRegistry: RoomRegistry
    private lateinit var messagingTemplate: SimpMessagingTemplate
    private lateinit var cleanup: DisconnectCleanupService

    @BeforeEach
    fun setup() {
        gameService = TestServiceFactory.createGameService()
        roomRegistry = RoomRegistry()
        messagingTemplate = mock(SimpMessagingTemplate::class.java)
        cleanup = DisconnectCleanupService(gameService, roomRegistry, messagingTemplate)
    }

    private fun createRoomWithTwoPlayers(): Room {
        val room = roomRegistry.createRoom(GameMode.DUAL_VALLEY)
        gameService.handleJoin(room.gameState, "Alice", "sess-alice")
        gameService.handleJoin(room.gameState, "Bob", "sess-bob")
        return room
    }

    @Test
    fun `connected player is not removed`() {
        val room = createRoomWithTwoPlayers()

        cleanup.cleanupExpired()

        assertEquals(2, room.gameState.players.size)
        verifyNoInteractions(messagingTemplate)
    }

    @Test
    fun `recently disconnected player within grace is not removed`() {
        val room = createRoomWithTwoPlayers()
        val alice = room.gameState.players.first { it.name == "Alice" }
        alice.connected = false
        alice.disconnectedAt = System.currentTimeMillis() - 5_000L   // 5s, innerhalb 30s Grace

        cleanup.cleanupExpired()

        assertEquals(2, room.gameState.players.size)
        assertFalse(alice.connected)
        verifyNoInteractions(messagingTemplate)
    }

    @Test
    fun `player past grace period is hard-deleted and broadcast is sent`() {
        val room = createRoomWithTwoPlayers()
        val alice = room.gameState.players.first { it.name == "Alice" }
        alice.connected = false
        alice.disconnectedAt = System.currentTimeMillis() - 60_000L   // 60s, weit ausserhalb 30s Grace

        cleanup.cleanupExpired()

        // Alice ist weg, Bob bleibt
        assertEquals(1, room.gameState.players.size)
        assertEquals("Bob", room.gameState.players.first().name)
        // Felder von Alice sind neutral (via eliminatePlayer)
        assertTrue(room.gameState.fields.none { it.owner == "Alice" })
        // Broadcast wurde gesendet
        verify(messagingTemplate).convertAndSend(
            ArgumentMatchers.eq("/topic/rooms/${room.roomId}/state"),
            ArgumentMatchers.any(GameState::class.java)
        )
    }

    @Test
    fun `cleanup runs independently across rooms`() {
        val room1 = createRoomWithTwoPlayers()
        val room2 = createRoomWithTwoPlayers()

        // Alice im Room1 ist abgelaufen
        val alice1 = room1.gameState.players.first { it.name == "Alice" }
        alice1.connected = false
        alice1.disconnectedAt = System.currentTimeMillis() - 60_000L

        // Im Room2 ist niemand abgelaufen
        cleanup.cleanupExpired()

        assertEquals(1, room1.gameState.players.size)
        assertEquals(2, room2.gameState.players.size)
        // Broadcast nur fuer Room1
        verify(messagingTemplate).convertAndSend(
            ArgumentMatchers.eq("/topic/rooms/${room1.roomId}/state"),
            ArgumentMatchers.any(GameState::class.java)
        )
        verify(messagingTemplate, never()).convertAndSend(
            ArgumentMatchers.eq("/topic/rooms/${room2.roomId}/state"),
            ArgumentMatchers.any(GameState::class.java)
        )
    }

    @Test
    fun `player with null disconnectedAt is treated as not expired`() {
        val room = createRoomWithTwoPlayers()
        val alice = room.gameState.players.first { it.name == "Alice" }
        // connected = false aber disconnectedAt nie gesetzt — defensiver Edge-Case
        alice.connected = false
        alice.disconnectedAt = null

        cleanup.cleanupExpired()

        assertEquals(2, room.gameState.players.size)
    }
}
