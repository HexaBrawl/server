package at.aau.hexabrawl.websocketserver.controller

import at.aau.hexabrawl.websocketserver.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.SimpMessagingTemplate

/**
 * Tests fuer den Reconnect Room-Endpoint.
 *
 * Deckt Happy Path + alle Validierungs-Pfade ab.
 */
class ReconnectRoomTest {

    private lateinit var controller: WebSocketBrokerController
    private lateinit var gameService: GameService
    private lateinit var roomRegistry: RoomRegistry
    private lateinit var messagingTemplate: SimpMessagingTemplate
    private lateinit var aliceHeader: SimpMessageHeaderAccessor
    private lateinit var bobHeader: SimpMessageHeaderAccessor

    @BeforeEach
    fun setup() {
        gameService = GameService(CombatService())
        roomRegistry = RoomRegistry()
        messagingTemplate = mock(SimpMessagingTemplate::class.java)
        controller = WebSocketBrokerController(gameService, roomRegistry, messagingTemplate)
        aliceHeader = mock(SimpMessageHeaderAccessor::class.java)
        `when`(aliceHeader.sessionId).thenReturn("session-alice")
        bobHeader = mock(SimpMessageHeaderAccessor::class.java)
        `when`(bobHeader.sessionId).thenReturn("session-bob")
    }

    /** Erzeugt Room mit Alice + Bob (IN_PROGRESS); Alice ist im Soft-Disconnect-State. */
    private fun setupRoomWithDisconnectedAlice(): Room {
        val room = roomRegistry.createRoom(GameMode.DUAL_VALLEY)
        controller.joinRoom(
            room.roomId,
            JoinRequest(name = "Alice", color = PlayerColor.RED),
            aliceHeader
        )
        controller.joinRoom(
            room.roomId,
            JoinRequest(name = "Bob", color = PlayerColor.BLUE),
            bobHeader
        )
        // Alice disconnected (Soft-Disconnect)
        gameService.handleDisconnect(room.gameState, "session-alice")
        return room
    }

    @Test
    fun `reconnect restores connected flag and binds new sessionId`() {
        val room = setupRoomWithDisconnectedAlice()
        val newHeader = mock(SimpMessageHeaderAccessor::class.java)
        `when`(newHeader.sessionId).thenReturn("session-alice-new")

        val result = controller.reconnectRoom(
            room.roomId,
            ReconnectRequest(playerName = "Alice", joinCode = room.joinCode),
            newHeader
        )

        assertNotNull(result)
        val alice = room.gameState.players.first { it.name == "Alice" }
        assertTrue(alice.connected)
        assertNull(alice.disconnectedAt)
        assertEquals("session-alice-new", alice.sessionId)
    }

    @Test
    fun `reconnect sends ROOM_NOT_FOUND for invalid roomId`() {
        val result = controller.reconnectRoom(
            "invalid-room",
            ReconnectRequest(playerName = "Alice", joinCode = "anything"),
            aliceHeader
        )

        assertNull(result)
        verify(messagingTemplate).convertAndSendToUser(
            eq("session-alice"),
            eq("/queue/errors"),
            argThat { it is ErrorMessage && it.errorCode == ErrorCode.ROOM_NOT_FOUND }
        )
    }

    @Test
    fun `reconnect sends RECONNECT_REJECTED for wrong joinCode`() {
        val room = setupRoomWithDisconnectedAlice()

        val result = controller.reconnectRoom(
            room.roomId,
            ReconnectRequest(playerName = "Alice", joinCode = "WRONG"),
            aliceHeader
        )

        assertNull(result)
        verify(messagingTemplate).convertAndSendToUser(
            eq("session-alice"),
            eq("/queue/errors"),
            argThat { it is ErrorMessage && it.errorCode == ErrorCode.RECONNECT_REJECTED }
        )
    }

    @Test
    fun `reconnect sends RECONNECT_REJECTED for unknown player`() {
        val room = setupRoomWithDisconnectedAlice()

        val result = controller.reconnectRoom(
            room.roomId,
            ReconnectRequest(playerName = "Stranger", joinCode = room.joinCode),
            aliceHeader
        )

        assertNull(result)
        verify(messagingTemplate).convertAndSendToUser(
            eq("session-alice"),
            eq("/queue/errors"),
            argThat { it is ErrorMessage && it.errorCode == ErrorCode.RECONNECT_REJECTED }
        )
    }

    @Test
    fun `reconnect sends RECONNECT_REJECTED when player is still connected`() {
        val room = roomRegistry.createRoom(GameMode.DUAL_VALLEY)
        controller.joinRoom(
            room.roomId,
            JoinRequest(name = "Alice", color = PlayerColor.RED),
            aliceHeader
        )
        controller.joinRoom(
            room.roomId,
            JoinRequest(name = "Bob", color = PlayerColor.BLUE),
            bobHeader
        )
        // KEIN handleDisconnect — Alice ist noch connected

        val result = controller.reconnectRoom(
            room.roomId,
            ReconnectRequest(playerName = "Alice", joinCode = room.joinCode),
            aliceHeader
        )

        assertNull(result)
        verify(messagingTemplate).convertAndSendToUser(
            eq("session-alice"),
            eq("/queue/errors"),
            argThat { it is ErrorMessage && it.errorCode == ErrorCode.RECONNECT_REJECTED }
        )
    }
}
