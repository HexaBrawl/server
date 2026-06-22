package at.aau.hexabrawl.websocketserver.controller

import at.aau.hexabrawl.websocketserver.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.SimpMessagingTemplate
import at.aau.hexabrawl.websocketserver.TestServiceFactory
import at.aau.hexabrawl.websocketserver.service.EconomyService
import at.aau.hexabrawl.websocketserver.service.GameService
import at.aau.hexabrawl.websocketserver.service.PlayerService

/**
 * Tests fuer den Cheat-Geschenk respond-steal Room-Endpoint.
 *
 * Deckt Happy Path (Steal + Decline) und alle Validierungs-Pfade ab.
 */
class RespondCheatStealRoomTest {

    private lateinit var controller: CheatController
    private lateinit var lobbyController: LobbyController
    private lateinit var gameService: GameService
    private lateinit var playerService: PlayerService
    private lateinit var economyService: EconomyService
    private lateinit var roomRegistry: RoomRegistry
    private lateinit var messagingTemplate: SimpMessagingTemplate
    private lateinit var aliceHeader: SimpMessageHeaderAccessor
    private lateinit var bobHeader: SimpMessageHeaderAccessor

    @BeforeEach
    fun setup() {
        gameService = TestServiceFactory.createGameService()
        playerService = TestServiceFactory.createPlayerService()
        economyService = EconomyService()
        roomRegistry = RoomRegistry()
        messagingTemplate = mock(SimpMessagingTemplate::class.java)
        val contextResolver = GameContextResolver(roomRegistry, messagingTemplate)
        controller = CheatController(gameService, contextResolver, messagingTemplate)
        lobbyController = LobbyController(playerService, economyService, contextResolver, messagingTemplate)
        aliceHeader = mock(SimpMessageHeaderAccessor::class.java)
        `when`(aliceHeader.sessionId).thenReturn("session-alice")
        bobHeader = mock(SimpMessageHeaderAccessor::class.java)
        `when`(bobHeader.sessionId).thenReturn("session-bob")
    }

    /** Helfer: erzeugt einen Room mit Alice + Bob (Status IN_PROGRESS) und aktivem pendingGift von Alice. */
    private fun setupRoomWithPendingGift(delta: Int): Room {
        val room = roomRegistry.createRoom(GameMode.DUAL_VALLEY)
        lobbyController.joinRoom(
            room.roomId,
            JoinRequest(name = "Alice", color = PlayerColor.RED),
            aliceHeader
        )
        lobbyController.joinRoom(
            room.roomId,
            JoinRequest(name = "Bob", color = PlayerColor.BLUE),
            bobHeader
        )
        // Alice oeffnet ein Geschenk
        controller.claimCheatGiftRoom(
            room.roomId,
            ClaimGiftRequest("Alice", delta),
            aliceHeader
        )
        return room
    }

    // ---- Happy Path ----------------------------------------------------

    @Test
    fun `respondCheatSteal accept transfers gold and clears pendingGift`() {
        val room = setupRoomWithPendingGift(delta = 5)
        val alice = room.gameState.players.first { it.name == "Alice" }
        val bob = room.gameState.players.first { it.name == "Bob" }
        val aliceGoldBefore = alice.gold
        val bobGoldBefore = bob.gold

        val result = controller.respondCheatStealRoom(
            room.roomId,
            StealResponseRequest("Bob", true),
            bobHeader
        )

        assertNotNull(result)
        assertEquals(aliceGoldBefore - 5, alice.gold)
        assertEquals(bobGoldBefore + 5, bob.gold)
        assertNull(result!!.pendingGift)
    }

    @Test
    fun `respondCheatSteal decline clears pendingGift when last decision`() {
        val room = setupRoomWithPendingGift(delta = 5)
        // 2 Spieler → pendingDecisions = 1 (nur Bob entscheidet)

        val result = controller.respondCheatStealRoom(
            room.roomId,
            StealResponseRequest("Bob", false),
            bobHeader
        )

        assertNotNull(result)
        assertNull(result!!.pendingGift)
    }

    // ---- Validierungs-Pfade --------------------------------------------

    @Test
    fun `respondCheatSteal sends ROOM_NOT_FOUND for invalid roomId`() {
        val result = controller.respondCheatStealRoom(
            "invalid-room",
            StealResponseRequest("Bob", true),
            bobHeader
        )

        assertNull(result)
        verify(messagingTemplate).convertAndSendToUser(
            eq("session-bob"),
            eq("/queue/errors"),
            argThat { it is ErrorMessage && it.errorCode == ErrorCode.ROOM_NOT_FOUND }
        )
    }

    @Test
    fun `respondCheatSteal sends NO_PENDING_GIFT when nothing is pending`() {
        val room = roomRegistry.createRoom(GameMode.DUAL_VALLEY)
        lobbyController.joinRoom(
            room.roomId,
            JoinRequest(name = "Alice", color = PlayerColor.RED),
            aliceHeader
        )
        lobbyController.joinRoom(
            room.roomId,
            JoinRequest(name = "Bob", color = PlayerColor.BLUE),
            bobHeader
        )
        // KEIN claimCheatGiftRoom-Call

        val result = controller.respondCheatStealRoom(
            room.roomId,
            StealResponseRequest("Bob", true),
            bobHeader
        )

        assertNull(result)
        verify(messagingTemplate).convertAndSendToUser(
            eq("session-bob"),
            eq("/queue/errors"),
            argThat { it is ErrorMessage && it.errorCode == ErrorCode.NO_PENDING_GIFT }
        )
    }

    @Test
    fun `respondCheatSteal sends OWNER_CANNOT_STEAL when owner tries to steal`() {
        val room = setupRoomWithPendingGift(delta = 5)

        // Alice (Owner) versucht selbst zu klauen
        val result = controller.respondCheatStealRoom(
            room.roomId,
            StealResponseRequest("Alice", true),
            aliceHeader
        )

        assertNull(result)
        verify(messagingTemplate).convertAndSendToUser(
            eq("session-alice"),
            eq("/queue/errors"),
            argThat { it is ErrorMessage && it.errorCode == ErrorCode.OWNER_CANNOT_STEAL }
        )
        // pendingGift bleibt aktiv
        assertNotNull(room.gameState.pendingGift)
    }

    @Test
    fun `respondCheatSteal second accept attempt sends NO_PENDING_GIFT`() {
        val room = setupRoomWithPendingGift(delta = 5)

        // Bob klaut zuerst
        controller.respondCheatStealRoom(
            room.roomId,
            StealResponseRequest("Bob", true),
            bobHeader
        )
        // Zweiter Versuch — pendingGift ist bereits null
        val result = controller.respondCheatStealRoom(
            room.roomId,
            StealResponseRequest("Bob", true),
            bobHeader
        )

        assertNull(result)
        verify(messagingTemplate, atLeastOnce()).convertAndSendToUser(
            eq("session-bob"),
            eq("/queue/errors"),
            argThat { it is ErrorMessage && it.errorCode == ErrorCode.NO_PENDING_GIFT }
        )
    }
}
