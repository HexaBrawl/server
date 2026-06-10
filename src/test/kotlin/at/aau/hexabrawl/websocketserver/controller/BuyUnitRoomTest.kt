package at.aau.hexabrawl.websocketserver.controller

import at.aau.hexabrawl.websocketserver.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.SimpMessagingTemplate

/**
 * Tests fuer den Buy-Unit Room-Endpoint (#132).
 *
 * Deckt Happy Path + alle 7 Validierungs-Pfade ab.
 */
class BuyUnitRoomTest {

    private lateinit var controller: WebSocketBrokerController
    private lateinit var gameService: GameService
    private lateinit var roomRegistry: RoomRegistry
    private lateinit var messagingTemplate: SimpMessagingTemplate
    private lateinit var headerAccessor: SimpMessageHeaderAccessor

    @BeforeEach
    fun setup() {
        gameService = GameService(CombatService())
        roomRegistry = RoomRegistry()
        messagingTemplate = mock(SimpMessagingTemplate::class.java)
        controller = WebSocketBrokerController(gameService, roomRegistry, messagingTemplate)
        headerAccessor = mock(SimpMessageHeaderAccessor::class.java)
        `when`(headerAccessor.sessionId).thenReturn("session-alice")
    }

    /** Helfer: erzeugt einen Room mit 2 Spielern (Alice am Zug, Alice owned Feld (3,3)). */
    private fun setupRoomWithAliceOwning(x: Int, y: Int): Room {
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

        // Zielfeld auf Alice's Ownership setzen
        room.gameState.fields.firstOrNull { it.x == x && it.y == y }?.owner = "Alice"
        return room
    }

    // ---- Happy Path ----------------------------------------------------

    @Test
    fun `buyUnit places unit with hasMovedThisTurn = true and deducts gold`() {
        val room = setupRoomWithAliceOwning(3, 3)
        val alice = room.gameState.players.first { it.name == "Alice" }
        alice.gold = 10

        val request = BuyUnitRequest("Alice", UnitType.INFANTRY, 3, 3)
        val result = controller.buyUnitRoom(room.roomId, request, headerAccessor)

        assertNotNull(result)
        assertEquals(10 - GameService.UNIT_PRICE, alice.gold)
        val placed = result!!.units.first { it.x == 3 && it.y == 3 && it.type == UnitType.INFANTRY }
        assertEquals("Alice", placed.player)
        assertTrue(placed.hasMovedThisTurn)
    }

    @Test
    fun `buyUnit removes skeleton on target field`() {
        val room = setupRoomWithAliceOwning(3, 3)
        val alice = room.gameState.players.first { it.name == "Alice" }
        alice.gold = 10
        room.gameState.units.add(GameUnit("Bob", 3, 3, UnitType.SKELETON))

        val request = BuyUnitRequest("Alice", UnitType.INFANTRY, 3, 3)
        val result = controller.buyUnitRoom(room.roomId, request, headerAccessor)

        assertNotNull(result)
        assertTrue(result!!.units.none { it.x == 3 && it.y == 3 && it.type == UnitType.SKELETON })
        assertTrue(result.units.any { it.x == 3 && it.y == 3 && it.type == UnitType.INFANTRY })
    }

    // ---- Validierungs-Pfade --------------------------------------------

    @Test
    fun `buyUnit sends ROOM_NOT_FOUND for invalid roomId`() {
        val request = BuyUnitRequest("Alice", UnitType.INFANTRY, 3, 3)
        val result = controller.buyUnitRoom("invalid-room", request, headerAccessor)
        assertNull(result)
        verify(messagingTemplate).convertAndSendToUser(
            eq("session-alice"),
            eq("/queue/errors"),
            argThat { it is ErrorMessage && it.errorCode == ErrorCode.ROOM_NOT_FOUND }
        )
    }

    @Test
    fun `buyUnit sends GAME_NOT_STARTED when game is not in progress`() {
        val room = roomRegistry.createRoom(GameMode.DUAL_VALLEY)
        // Nur ein Spieler -> Status WAITING_FOR_PLAYERS
        controller.joinRoom(
            room.roomId,
            JoinRequest(name = "Alice"),
            headerAccessor
        )

        val request = BuyUnitRequest("Alice", UnitType.INFANTRY, 3, 3)
        val result = controller.buyUnitRoom(room.roomId, request, headerAccessor)

        assertNull(result)
        verify(messagingTemplate).convertAndSendToUser(
            eq("session-alice"),
            eq("/queue/errors"),
            argThat { it is ErrorMessage && it.errorCode == ErrorCode.GAME_NOT_STARTED }
        )
    }

    @Test
    fun `buyUnit sends NOT_YOUR_TURN when other player tries`() {
        val room = setupRoomWithAliceOwning(3, 3)
        // Bob versucht zu kaufen, Alice ist am Zug
        val bobHeader = mock(SimpMessageHeaderAccessor::class.java)
        `when`(bobHeader.sessionId).thenReturn("session-bob")

        val request = BuyUnitRequest("Bob", UnitType.INFANTRY, 3, 3)
        val result = controller.buyUnitRoom(room.roomId, request, bobHeader)

        assertNull(result)
        verify(messagingTemplate).convertAndSendToUser(
            eq("session-bob"),
            eq("/queue/errors"),
            argThat { it is ErrorMessage && it.errorCode == ErrorCode.NOT_YOUR_TURN }
        )
    }

    @Test
    fun `buyUnit sends INVALID_PLACEMENT for type BASE`() {
        val room = setupRoomWithAliceOwning(3, 3)
        val request = BuyUnitRequest("Alice", UnitType.BASE, 3, 3)
        val result = controller.buyUnitRoom(room.roomId, request, headerAccessor)

        assertNull(result)
        verify(messagingTemplate).convertAndSendToUser(
            eq("session-alice"),
            eq("/queue/errors"),
            argThat { it is ErrorMessage && it.errorCode == ErrorCode.INVALID_PLACEMENT }
        )
    }

    @Test
    fun `buyUnit sends INVALID_PLACEMENT for type SKELETON`() {
        val room = setupRoomWithAliceOwning(3, 3)
        val request = BuyUnitRequest("Alice", UnitType.SKELETON, 3, 3)
        val result = controller.buyUnitRoom(room.roomId, request, headerAccessor)

        assertNull(result)
        verify(messagingTemplate).convertAndSendToUser(
            eq("session-alice"),
            eq("/queue/errors"),
            argThat { it is ErrorMessage && it.errorCode == ErrorCode.INVALID_PLACEMENT }
        )
    }

    @Test
    fun `buyUnit sends INVALID_PLACEMENT when field is neutral`() {
        val room = setupRoomWithAliceOwning(3, 3)
        // Anderes Feld (5,5) ist neutral
        val request = BuyUnitRequest("Alice", UnitType.INFANTRY, 5, 5)
        val result = controller.buyUnitRoom(room.roomId, request, headerAccessor)

        assertNull(result)
        verify(messagingTemplate).convertAndSendToUser(
            eq("session-alice"),
            eq("/queue/errors"),
            argThat { it is ErrorMessage && it.errorCode == ErrorCode.INVALID_PLACEMENT }
        )
    }

    @Test
    fun `buyUnit sends INVALID_PLACEMENT when field belongs to enemy`() {
        val room = setupRoomWithAliceOwning(3, 3)
        // Gegnerisches Feld
        room.gameState.fields.firstOrNull { it.x == 7 && it.y == 7 }?.owner = "Bob"

        val request = BuyUnitRequest("Alice", UnitType.INFANTRY, 7, 7)
        val result = controller.buyUnitRoom(room.roomId, request, headerAccessor)

        assertNull(result)
        verify(messagingTemplate).convertAndSendToUser(
            eq("session-alice"),
            eq("/queue/errors"),
            argThat { it is ErrorMessage && it.errorCode == ErrorCode.INVALID_PLACEMENT }
        )
    }

    @Test
    fun `buyUnit sends INVALID_PLACEMENT when own unit blocks field`() {
        val room = setupRoomWithAliceOwning(3, 3)
        room.gameState.units.add(GameUnit("Alice", 3, 3, UnitType.INFANTRY))

        val request = BuyUnitRequest("Alice", UnitType.CAVALRY, 3, 3)
        val result = controller.buyUnitRoom(room.roomId, request, headerAccessor)

        assertNull(result)
        verify(messagingTemplate).convertAndSendToUser(
            eq("session-alice"),
            eq("/queue/errors"),
            argThat { it is ErrorMessage && it.errorCode == ErrorCode.INVALID_PLACEMENT }
        )
    }

    @Test
    fun `buyUnit sends INVALID_PLACEMENT when own BASE blocks field`() {
        val room = setupRoomWithAliceOwning(3, 3)
        room.gameState.units.add(GameUnit("Alice", 3, 3, UnitType.BASE))

        val request = BuyUnitRequest("Alice", UnitType.INFANTRY, 3, 3)
        val result = controller.buyUnitRoom(room.roomId, request, headerAccessor)

        assertNull(result)
        verify(messagingTemplate).convertAndSendToUser(
            eq("session-alice"),
            eq("/queue/errors"),
            argThat { it is ErrorMessage && it.errorCode == ErrorCode.INVALID_PLACEMENT }
        )
    }

    @Test
    fun `buyUnit sends INSUFFICIENT_GOLD when player is poor`() {
        val room = setupRoomWithAliceOwning(3, 3)
        val alice = room.gameState.players.first { it.name == "Alice" }
        alice.gold = 2  // weniger als UNIT_PRICE (5)

        val request = BuyUnitRequest("Alice", UnitType.INFANTRY, 3, 3)
        val result = controller.buyUnitRoom(room.roomId, request, headerAccessor)

        assertNull(result)
        verify(messagingTemplate).convertAndSendToUser(
            eq("session-alice"),
            eq("/queue/errors"),
            argThat { it is ErrorMessage && it.errorCode == ErrorCode.INSUFFICIENT_GOLD }
        )
    }
}
