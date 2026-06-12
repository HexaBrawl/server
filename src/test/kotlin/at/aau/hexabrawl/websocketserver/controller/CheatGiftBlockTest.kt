package at.aau.hexabrawl.websocketserver.controller

import at.aau.hexabrawl.websocketserver.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.SimpMessagingTemplate

/**
 * Tests, dass /move, /end-turn, /buy-farm und /buy-unit blockiert sind
 * waehrend ein Cheat-Geschenk auf Antwort wartet (GIFT_PENDING).
 */
class CheatGiftBlockTest {

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

    /** Erzeugt Room mit Alice+Bob (Status IN_PROGRESS) und aktivem pendingGift von Alice. */
    private fun setupRoomWithPendingGift(): Room {
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
        controller.claimCheatGiftRoom(
            room.roomId,
            ClaimGiftRequest("Alice", 5),
            aliceHeader
        )
        return room
    }

    @Test
    fun `moveRoom is blocked during pendingGift`() {
        val room = setupRoomWithPendingGift()

        val result = controller.moveRoom(
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

        val result = controller.endTurnRoom(
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

        val result = controller.buyFarmRoom(room.roomId, aliceHeader)

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

        val result = controller.buyUnitRoom(
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
