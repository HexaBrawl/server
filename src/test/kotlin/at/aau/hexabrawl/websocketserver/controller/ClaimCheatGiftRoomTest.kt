package at.aau.hexabrawl.websocketserver.controller

import at.aau.hexabrawl.websocketserver.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.SimpMessagingTemplate
import at.aau.hexabrawl.websocketserver.TestServiceFactory

/**
 * Tests fuer den Cheat-Geschenk claim-gift Room-Endpoint.
 *
 * Deckt Happy Path + alle 6 Validierungs-Pfade ab.
 */
class ClaimCheatGiftRoomTest {

    private lateinit var controller: WebSocketBrokerController
    private lateinit var gameService: GameService
    private lateinit var roomRegistry: RoomRegistry
    private lateinit var messagingTemplate: SimpMessagingTemplate
    private lateinit var headerAccessor: SimpMessageHeaderAccessor

    @BeforeEach
    fun setup() {
        gameService = TestServiceFactory.createGameService()
        roomRegistry = RoomRegistry()
        messagingTemplate = mock(SimpMessagingTemplate::class.java)
        controller = WebSocketBrokerController(gameService, roomRegistry, messagingTemplate)
        headerAccessor = mock(SimpMessageHeaderAccessor::class.java)
        `when`(headerAccessor.sessionId).thenReturn("session-alice")
    }

    /** Helfer: erzeugt einen Room mit 2 Spielern (Alice + Bob, Status IN_PROGRESS). */
    private fun setupRoomInProgress(): Room {
        val room = roomRegistry.createRoom(GameMode.DUAL_VALLEY)
        controller.joinRoom(
            room.roomId,
            JoinRequest(name = "Alice", color = PlayerColor.RED),
            headerAccessor
        )
        val bobHeader = mock(SimpMessageHeaderAccessor::class.java)
        `when`(bobHeader.sessionId).thenReturn("session-bob")
        controller.joinRoom(
            room.roomId,
            JoinRequest(name = "Bob", color = PlayerColor.BLUE),
            bobHeader
        )
        return room
    }

    // ---- Happy Path ----------------------------------------------------

    @Test
    fun `claimCheatGift adds delta sets hasUsedGift and pendingGift`() {
        val room = setupRoomInProgress()
        val alice = room.gameState.players.first { it.name == "Alice" }
        alice.gold = 5

        val request = ClaimGiftRequest("Alice", 7)
        val result = controller.claimCheatGiftRoom(room.roomId, request, headerAccessor)

        assertNotNull(result)
        assertEquals(12, alice.gold)
        assertTrue(alice.hasUsedGift)
        assertNotNull(result!!.pendingGift)
        assertEquals("Alice", result.pendingGift?.ownerName)
        assertEquals(7, result.pendingGift?.delta)
        assertEquals(1, result.pendingGift?.pendingDecisions)
    }

    @Test
    fun `claimCheatGift caps gold at zero on negative delta`() {
        val room = setupRoomInProgress()
        val alice = room.gameState.players.first { it.name == "Alice" }
        alice.gold = 3

        val request = ClaimGiftRequest("Alice", -10)
        val result = controller.claimCheatGiftRoom(room.roomId, request, headerAccessor)

        assertNotNull(result)
        assertEquals(0, alice.gold)
        assertTrue(alice.hasUsedGift)
    }

    // ---- Validierungs-Pfade --------------------------------------------

    @Test
    fun `claimCheatGift sends ROOM_NOT_FOUND for invalid roomId`() {
        val request = ClaimGiftRequest("Alice", 5)
        val result = controller.claimCheatGiftRoom("invalid-room", request, headerAccessor)

        assertNull(result)
        verify(messagingTemplate).convertAndSendToUser(
            eq("session-alice"),
            eq("/queue/errors"),
            argThat { it is ErrorMessage && it.errorCode == ErrorCode.ROOM_NOT_FOUND }
        )
    }

    @Test
    fun `claimCheatGift sends GAME_NOT_STARTED when game is waiting`() {
        val room = roomRegistry.createRoom(GameMode.DUAL_VALLEY)
        controller.joinRoom(
            room.roomId,
            JoinRequest(name = "Alice"),
            headerAccessor
        )

        val request = ClaimGiftRequest("Alice", 5)
        val result = controller.claimCheatGiftRoom(room.roomId, request, headerAccessor)

        assertNull(result)
        verify(messagingTemplate).convertAndSendToUser(
            eq("session-alice"),
            eq("/queue/errors"),
            argThat { it is ErrorMessage && it.errorCode == ErrorCode.GAME_NOT_STARTED }
        )
    }

    @Test
    fun `claimCheatGift sends INVALID_CHEAT_DELTA for delta above max`() {
        val room = setupRoomInProgress()

        val request = ClaimGiftRequest("Alice", 15)
        val result = controller.claimCheatGiftRoom(room.roomId, request, headerAccessor)

        assertNull(result)
        verify(messagingTemplate).convertAndSendToUser(
            eq("session-alice"),
            eq("/queue/errors"),
            argThat { it is ErrorMessage && it.errorCode == ErrorCode.INVALID_CHEAT_DELTA }
        )
        assertNull(room.gameState.pendingGift)
    }

    @Test
    fun `claimCheatGift sends INVALID_CHEAT_DELTA for delta below min`() {
        val room = setupRoomInProgress()

        val request = ClaimGiftRequest("Alice", -11)
        val result = controller.claimCheatGiftRoom(room.roomId, request, headerAccessor)

        assertNull(result)
        verify(messagingTemplate).convertAndSendToUser(
            eq("session-alice"),
            eq("/queue/errors"),
            argThat { it is ErrorMessage && it.errorCode == ErrorCode.INVALID_CHEAT_DELTA }
        )
    }

    @Test
    fun `claimCheatGift sends CHEAT_ALREADY_PENDING when one is active`() {
        val room = setupRoomInProgress()
        // Alice oeffnet zuerst
        controller.claimCheatGiftRoom(room.roomId, ClaimGiftRequest("Alice", 5), headerAccessor)

        // Bob versucht zu oeffnen waehrend Alice's pendingGift aktiv ist
        val bobHeader = mock(SimpMessageHeaderAccessor::class.java)
        `when`(bobHeader.sessionId).thenReturn("session-bob")
        val result = controller.claimCheatGiftRoom(room.roomId, ClaimGiftRequest("Bob", 5), bobHeader)

        assertNull(result)
        verify(messagingTemplate).convertAndSendToUser(
            eq("session-bob"),
            eq("/queue/errors"),
            argThat { it is ErrorMessage && it.errorCode == ErrorCode.CHEAT_ALREADY_PENDING }
        )
    }

    @Test
    fun `claimCheatGift sends CHEAT_ALREADY_USED on second attempt by same player`() {
        val room = setupRoomInProgress()
        // Alice oeffnet zuerst
        controller.claimCheatGiftRoom(room.roomId, ClaimGiftRequest("Alice", 5), headerAccessor)
        // pendingGift simuliert beendet (z.B. alle haben Nein gesagt)
        room.gameState.pendingGift = null

        // Alice versucht erneut
        val result = controller.claimCheatGiftRoom(room.roomId, ClaimGiftRequest("Alice", 5), headerAccessor)

        assertNull(result)
        verify(messagingTemplate).convertAndSendToUser(
            eq("session-alice"),
            eq("/queue/errors"),
            argThat { it is ErrorMessage && it.errorCode == ErrorCode.CHEAT_ALREADY_USED }
        )
    }
}
