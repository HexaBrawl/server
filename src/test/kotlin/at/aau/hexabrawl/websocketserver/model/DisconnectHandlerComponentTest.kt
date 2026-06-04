package at.aau.hexabrawl.websocketserver.model

import org.junit.jupiter.api.Assertions.assertEquals
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
    fun `handleDisconnect removes player from game`() {

        val room = roomRegistry.createRoom(
            GameMode.DUAL_VALLEY
        )

        gameService.handleJoin(room.gameState,"Alice", "session-1")

        val event = Mockito.mock(SessionDisconnectEvent::class.java)
        Mockito.`when`(event.sessionId).thenReturn("session-1")

        handler.handleDisconnect(event)

        assertEquals(0, room.gameState.players.size)
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

        assertEquals(
            2,
            room1.gameState.players.size
        )

        assertEquals(
            1,
            room2.gameState.players.size
        )

        assertEquals(
            "Dave",
            room2.gameState.players.first().name
        )
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
    fun `disconnect sets only affected room to FINISHED`() {

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

        assertEquals(
            GameStatus.IN_PROGRESS,
            room1.gameState.status
        )

        assertEquals(
            GameStatus.IN_PROGRESS,
            room2.gameState.status
        )

        gameService.handleDisconnect(
            room2.gameState,
            "session-3"
        )

        assertEquals(
            GameStatus.IN_PROGRESS,
            room1.gameState.status
        )

        assertEquals(
            GameStatus.FINISHED,
            room2.gameState.status
        )
    }

}