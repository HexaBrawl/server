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
import at.aau.hexabrawl.websocketserver.service.TurnService

/**
 * Tests, dass /move, /end-turn, /buy-farm und /buy-unit blockiert sind
 * waehrend ein Cheat-Geschenk auf Antwort wartet (GIFT_PENDING).
 */
class CheatGiftBlockTest {

    private lateinit var lobbyController: LobbyController
    private lateinit var gameTurnController: GameTurnController
    private lateinit var purchaseController: PurchaseController
    private lateinit var cheatController: CheatController
    private lateinit var gameService: GameService
    private lateinit var economyService: EconomyService
    private lateinit var turnService: TurnService
    private lateinit var roomRegistry: RoomRegistry
    private lateinit var messagingTemplate: SimpMessagingTemplate
    private lateinit var aliceHeader: SimpMessageHeaderAccessor
    private lateinit var bobHeader: SimpMessageHeaderAccessor

    @BeforeEach
    fun setup() {
        gameService = TestServiceFactory.createGameService()
        economyService = EconomyService()
        turnService = TestServiceFactory.createTurnService()
        roomRegistry = RoomRegistry()
        messagingTemplate = mock(SimpMessagingTemplate::class.java)
        val contextResolver = GameContextResolver(roomRegistry, messagingTemplate)
        lobbyController = LobbyController(gameService, contextResolver, messagingTemplate)
        gameTurnController = GameTurnController(turnService, economyService, contextResolver, messagingTemplate)
        purchaseController = PurchaseController(economyService, contextResolver, messagingTemplate)
        cheatController = CheatController(gameService, contextResolver, messagingTemplate)
        aliceHeader = mock(SimpMessageHeaderAccessor::class.java)
        `when`(aliceHeader.sessionId).thenReturn("session-alice")
        bobHeader = mock(SimpMessageHeaderAccessor::class.java)
        `when`(bobHeader.sessionId).thenReturn("session-bob")
    }

    /** Erzeugt Room mit Alice+Bob (Status IN_PROGRESS) und aktivem pendingGift von Alice. */
    private fun setupRoomWithPendingGift(): Room {
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
        cheatController.claimCheatGiftRoom(
            room.roomId,
            ClaimGiftRequest("Alice", 5),
            aliceHeader
        )
        return room
    }

    @Test
    fun `moveRoom is blocked during pendingGift`() {
        val room = setupRoomWithPendingGift()

        val result = gameTurnController.moveRoom(
            room.roomId,
            Move(player = "Alice", type = UnitType.INFANTRY, fromX = 2, fromY = 2, toX = 3, toY = 2),
            aliceHeader
        )

        assertNull(result)
        verify(messagingTemplate).convertAndSendToUser(
            eq("session-alice"),
            eq("/queue/errors"),
            argThat { it is ErrorMessage && it.errorCode == ErrorCode.GIFT_PENDING }
        )
    }

    @Test
    fun `endTurnRoom is blocked during pendingGift`() {
        val room = setupRoomWithPendingGift()

        val result = gameTurnController.endTurnRoom(
            room.roomId,
            EndTurnRequest(playerName = "Alice"),
            aliceHeader
        )

        assertNull(result)
        verify(messagingTemplate).convertAndSendToUser(
            eq("session-alice"),
            eq("/queue/errors"),
            argThat { it is ErrorMessage && it.errorCode == ErrorCode.GIFT_PENDING }
        )
    }

    @Test
    fun `buyFarmRoom is blocked during pendingGift`() {
        val room = setupRoomWithPendingGift()

        val result = purchaseController.buyFarmRoom(room.roomId, aliceHeader)

        assertNull(result)
        verify(messagingTemplate).convertAndSendToUser(
            eq("session-alice"),
            eq("/queue/errors"),
            argThat { it is ErrorMessage && it.errorCode == ErrorCode.GIFT_PENDING }
        )
    }

    @Test
    fun `buyUnitRoom is blocked during pendingGift`() {
        val room = setupRoomWithPendingGift()

        val result = purchaseController.buyUnitRoom(
            room.roomId,
            BuyUnitRequest("Alice", UnitType.INFANTRY, 2, 2),
            aliceHeader
        )

        assertNull(result)
        verify(messagingTemplate).convertAndSendToUser(
            eq("session-alice"),
            eq("/queue/errors"),
            argThat { it is ErrorMessage && it.errorCode == ErrorCode.GIFT_PENDING }
        )
    }
}
