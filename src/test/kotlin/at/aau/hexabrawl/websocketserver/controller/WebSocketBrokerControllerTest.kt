package at.aau.hexabrawl.websocketserver.controller

import at.aau.hexabrawl.websocketserver.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.SimpMessagingTemplate

class WebSocketBrokerControllerTest {

    private lateinit var controller: WebSocketBrokerController
    private lateinit var gameService: GameService
    private lateinit var messagingTemplate: SimpMessagingTemplate // Neu für Issue #24
    private lateinit var headerAccessor: SimpMessageHeaderAccessor // Neu für Issue #24

    @BeforeEach
    fun setup() {
        gameService = GameService()
        messagingTemplate = mock(SimpMessagingTemplate::class.java) // Mock erstellen
        controller = WebSocketBrokerController(gameService, messagingTemplate)

        headerAccessor = mock(SimpMessageHeaderAccessor::class.java)
        `when`(headerAccessor.sessionId).thenReturn("test-session")
    }

    @Test
    fun `player can join game`() {
        val state = controller.handleJoin("Josef")
        Assertions.assertTrue(state.players.any { it.name == "Josef" })
        Assertions.assertEquals(1, state.players.size)
    }

    @Test
    fun `init returns current state`() {
        controller.handleJoin("Alice")
        controller.handleJoin("Bob")
        val state = controller.init()
        assertEquals(2, state.players.size)
    }

    @Test
    fun `join via websocket sends GAME_FULL error when full`() {
        controller.handleJoin("P1")
        controller.handleJoin("P2")

        val result = controller.join("P3", headerAccessor)

        assertNull(result)
        verify(messagingTemplate).convertAndSendToUser(
            eq("test-session"),
            eq("/queue/errors"),
            argThat { it is ErrorMessage && it.errorCode == ErrorCode.GAME_FULL }
        )
    }

    @Test
    fun `move via websocket sends GAME_NOT_STARTED error`() {
        val move = Move(player = "P1")

        val result = controller.move(move, headerAccessor)

        assertNull(result)
        verify(messagingTemplate).convertAndSendToUser(
            eq("test-session"),
            eq("/queue/errors"),
            argThat { it is ErrorMessage && it.errorCode == ErrorCode.GAME_NOT_STARTED }
        )
    }

    @Test
    fun `move via websocket sends NOT_YOUR_TURN error`() {
        controller.handleJoin("P1")
        controller.handleJoin("P2")

        val move = Move(player = "P2")
        val result = controller.move(move, headerAccessor)

        assertNull(result)
        verify(messagingTemplate).convertAndSendToUser(
            eq("test-session"),
            eq("/queue/errors"),
            argThat { it is ErrorMessage && it.errorCode == ErrorCode.NOT_YOUR_TURN }
        )
    }

    @Test
    fun `move via websocket sends INVALID_MOVE error`() {
        controller.handleJoin("P1")
        controller.handleJoin("P2")

        val move = Move(player = "P1", fromX = 0, fromY = 0, toX = 9, toY = 9)
        val result = controller.move(move, headerAccessor)

        assertNull(result)
        verify(messagingTemplate).convertAndSendToUser(
            eq("test-session"),
            eq("/queue/errors"),
            argThat { it is ErrorMessage && it.errorCode == ErrorCode.INVALID_MOVE }
        )
    }
}