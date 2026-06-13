package at.aau.hexabrawl.websocketserver.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.web.socket.messaging.SessionDisconnectEvent

class DisconnectHandlerComponentTest {

    private val gameService = GameService(CombatService())
    private val roomRegistry = RoomRegistry()
    private val messagingTemplate = Mockito.mock(SimpMessagingTemplate::class.java)
    private val handler = DisconnectHandler(gameService, roomRegistry, messagingTemplate)

    @Test
    fun `handleDisconnect broadcasts updated state`() {

        val room = roomRegistry.createRoom(
            GameMode.DUAL_VALLEY
        )

        gameService.handleJoin(
            room.gameState,
            "Alice",
            "session-1"
        )

        gameService.handleJoin(
            room.gameState,
            "Bob",
            "session-2"
        )

        val event = Mockito.mock(SessionDisconnectEvent::class.java)
        Mockito.`when`(event.sessionId).thenReturn("session-1")

        handler.handleDisconnect(event)

        Mockito.verify(messagingTemplate).convertAndSend(
            ArgumentMatchers.eq("/topic/rooms/${room.roomId}/state"),
            ArgumentMatchers.any(GameState::class.java)
        )
    }

    @Test
    fun `handleDisconnect marks player as not connected but keeps them`() {
        val room = roomRegistry.createRoom(GameMode.DUAL_VALLEY)
        gameService.handleJoin(room.gameState, "Alice", "session-1")

        val event = Mockito.mock(SessionDisconnectEvent::class.java)
        Mockito.`when`(event.sessionId).thenReturn("session-1")

        handler.handleDisconnect(event)

        // Soft-Disconnect: Player bleibt im State waehrend Grace Period
        assertEquals(1, room.gameState.players.size)
        assertFalse(room.gameState.players.first().connected)
    }

    @Test
    fun `handleDisconnect does not broadcast when player not found`() {
        val event = Mockito.mock(SessionDisconnectEvent::class.java)
        Mockito.`when`(event.sessionId).thenReturn("unknown-session")

        handler.handleDisconnect(event)

        Mockito.verifyNoInteractions(messagingTemplate)
    }

    @Test
    fun `disconnect removes player only from affected room`() {

        val room1 = roomRegistry.createRoom(
            GameMode.DUAL_VALLEY
        )

        val room2 = roomRegistry.createRoom(
            GameMode.DUAL_VALLEY
        )

        gameService.handleJoin(
            room1.gameState,
            "Alice",
            "session-1"
        )

        gameService.handleJoin(
            room1.gameState,
            "Bob",
            "session-2"
        )

        gameService.handleJoin(
            room2.gameState,
            "Charlie",
            "session-3"
        )

        gameService.handleJoin(
            room2.gameState,
            "Dave",
            "session-4"
        )

        val event = Mockito.mock(SessionDisconnectEvent::class.java)

        Mockito.`when`(event.sessionId)
            .thenReturn("session-3")

        handler.handleDisconnect(event)

        // Soft-Disconnect: room2.Charlie wurde markiert, nicht entfernt
        assertEquals(2, room1.gameState.players.size)
        assertEquals(2, room2.gameState.players.size)
        assertFalse(room2.gameState.players.first { it.name == "Charlie" }.connected)
        // Andere Spieler in room2 sind unbeeinflusst
        assertEquals(true, room2.gameState.players.first { it.name == "Dave" }.connected)
        // room1 ist komplett unbeeinflusst
        assertEquals(true, room1.gameState.players.first { it.name == "Alice" }.connected)
    }

    @Test
    fun `handleDisconnect broadcasts to room specific topic`() {

        val room = roomRegistry.createRoom(
            GameMode.DUAL_VALLEY
        )

        gameService.handleJoin(
            room.gameState,
            "Alice",
            "session-1"
        )

        gameService.handleJoin(
            room.gameState,
            "Bob",
            "session-2"
        )

        val event = Mockito.mock(SessionDisconnectEvent::class.java)

        Mockito.`when`(event.sessionId)
            .thenReturn("session-1")

        handler.handleDisconnect(event)

        Mockito.verify(messagingTemplate).convertAndSend(
            ArgumentMatchers.eq(
                "/topic/rooms/${room.roomId}/state"
            ),
            ArgumentMatchers.any(GameState::class.java)
        )
    }

    @Test
    fun `soft disconnect does not change status of either room`() {
        val room1 = roomRegistry.createRoom(GameMode.DUAL_VALLEY)
        val room2 = roomRegistry.createRoom(GameMode.DUAL_VALLEY)
        gameService.handleJoin(room1.gameState, "Alice", "session-1")
        gameService.handleJoin(room1.gameState, "Bob", "session-2")
        gameService.handleJoin(room2.gameState, "Charlie", "session-3")
        gameService.handleJoin(room2.gameState, "Dave", "session-4")

        assertEquals(GameStatus.IN_PROGRESS, room1.gameState.status)
        assertEquals(GameStatus.IN_PROGRESS, room2.gameState.status)

        gameService.handleDisconnect(room2.gameState, "session-3")

        // Soft-Disconnect: kein Statuswechsel — der passiert erst beim hardDelete
        assertEquals(GameStatus.IN_PROGRESS, room1.gameState.status)
        assertEquals(GameStatus.IN_PROGRESS, room2.gameState.status)
    }

    @Test
    fun `hardDelete in DUAL_VALLEY sets status to FINISHED`() {
        val room = roomRegistry.createRoom(GameMode.DUAL_VALLEY)
        gameService.handleJoin(room.gameState, "Alice", "session-1")
        gameService.handleJoin(room.gameState, "Bob", "session-2")

        val alice = room.gameState.players.first { it.name == "Alice" }
        gameService.hardDelete(room.gameState, alice)

        // Bob ist letzter mit BASE → Win
        assertEquals(GameStatus.FINISHED, room.gameState.status)
        assertEquals("Bob", room.gameState.winner)
        assertEquals(1, room.gameState.players.size)
    }
}