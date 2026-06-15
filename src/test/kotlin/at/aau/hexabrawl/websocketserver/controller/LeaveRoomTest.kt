package at.aau.hexabrawl.websocketserver.controller

import at.aau.hexabrawl.websocketserver.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.SimpMessagingTemplate
import at.aau.hexabrawl.websocketserver.TestServiceFactory
import at.aau.hexabrawl.websocketserver.service.GameService

/**
 * Tests fuer den /leave Room-Endpoint.
 *
 * Deckt Happy Path + alle Validierungs-Pfade ab.
 */
class LeaveRoomTest {

    private lateinit var controller: LobbyController
    private lateinit var gameService: GameService
    private lateinit var roomRegistry: RoomRegistry
    private lateinit var messagingTemplate: SimpMessagingTemplate
    private lateinit var aliceHeader: SimpMessageHeaderAccessor
    private lateinit var bobHeader: SimpMessageHeaderAccessor

    @BeforeEach
    fun setup() {
        gameService = TestServiceFactory.createGameService()
        roomRegistry = RoomRegistry()
        messagingTemplate = mock(SimpMessagingTemplate::class.java)
        val contextResolver = GameContextResolver(roomRegistry, messagingTemplate)
        controller = LobbyController(gameService, contextResolver, messagingTemplate)
        aliceHeader = mock(SimpMessageHeaderAccessor::class.java)
        `when`(aliceHeader.sessionId).thenReturn("session-alice")
        bobHeader = mock(SimpMessageHeaderAccessor::class.java)
        `when`(bobHeader.sessionId).thenReturn("session-bob")
    }

    private fun createRoomWith(mode: GameMode, vararg names: Pair<String, SimpMessageHeaderAccessor>): Room {
        val room = roomRegistry.createRoom(mode)
        names.forEachIndexed { index, (name, header) ->
            val color = listOf(PlayerColor.RED, PlayerColor.BLUE, PlayerColor.GREEN, PlayerColor.YELLOW)[index]
            controller.joinRoom(room.roomId, JoinRequest(name = name, color = color), header)
        }
        return room
    }

    @Test
    fun `leave immediately hard-deletes player in DUAL_VALLEY`() {
        val room = createRoomWith(
            GameMode.DUAL_VALLEY,
            "Alice" to aliceHeader,
            "Bob" to bobHeader
        )

        val result = controller.leaveRoom(
            room.roomId,
            LeaveRequest(playerName = "Alice"),
            aliceHeader
        )

        assertNotNull(result)
        assertEquals(1, room.gameState.players.size)
        assertEquals("Bob", room.gameState.players.first().name)
        // Alice's Felder sind neutral
        assertTrue(room.gameState.fields.none { it.owner == "Alice" })
        // DUAL_VALLEY: Bob ist letzter mit BASE → Win
        assertEquals(GameStatus.FINISHED, room.gameState.status)
        assertEquals("Bob", room.gameState.winner)
    }

    @Test
    fun `leave in TRIAD keeps game running with remaining players`() {
        val carolHeader = mock(SimpMessageHeaderAccessor::class.java)
        `when`(carolHeader.sessionId).thenReturn("session-carol")
        val room = createRoomWith(
            GameMode.TRIAD_OUTPOST,
            "Alice" to aliceHeader,
            "Bob" to bobHeader,
            "Carol" to carolHeader
        )

        controller.leaveRoom(
            room.roomId,
            LeaveRequest(playerName = "Alice"),
            aliceHeader
        )

        assertEquals(2, room.gameState.players.size)
        assertEquals(GameStatus.IN_PROGRESS, room.gameState.status)
        assertTrue(room.gameState.fields.none { it.owner == "Alice" })
    }

    @Test
    fun `leave with wrong sessionId is a no-op`() {
        val room = createRoomWith(
            GameMode.DUAL_VALLEY,
            "Alice" to aliceHeader,
            "Bob" to bobHeader
        )
        // Bob versucht Alice rauszuwerfen (Manipulation)
        val result = controller.leaveRoom(
            room.roomId,
            LeaveRequest(playerName = "Alice"),
            bobHeader   // ← sessionId von Bob, nicht Alice
        )

        assertNull(result)
        assertEquals(2, room.gameState.players.size)
    }

    @Test
    fun `leave with unknown player is a no-op`() {
        val room = createRoomWith(
            GameMode.DUAL_VALLEY,
            "Alice" to aliceHeader,
            "Bob" to bobHeader
        )

        val result = controller.leaveRoom(
            room.roomId,
            LeaveRequest(playerName = "Stranger"),
            aliceHeader
        )

        assertNull(result)
        assertEquals(2, room.gameState.players.size)
    }

    @Test
    fun `leave sends ROOM_NOT_FOUND for invalid roomId`() {
        val result = controller.leaveRoom(
            "invalid-room",
            LeaveRequest(playerName = "Alice"),
            aliceHeader
        )

        assertNull(result)
        verify(messagingTemplate).convertAndSendToUser(
            eq("session-alice"),
            eq("/queue/errors"),
            argThat { it is ErrorMessage && it.errorCode == ErrorCode.ROOM_NOT_FOUND }
        )
    }
}
