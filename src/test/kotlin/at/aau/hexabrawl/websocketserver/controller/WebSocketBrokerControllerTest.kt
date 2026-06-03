package at.aau.hexabrawl.websocketserver.controller

import at.aau.hexabrawl.websocketserver.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
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
        gameService = GameService(CombatService())
        messagingTemplate = mock(SimpMessagingTemplate::class.java) // Mock erstellen
        controller = WebSocketBrokerController(gameService, messagingTemplate)

        headerAccessor = mock(SimpMessageHeaderAccessor::class.java)
        `when`(headerAccessor.sessionId).thenReturn("test-session")
    }

    @Test
    fun `player can join game`() {
        val state = controller.handleJoin("Josef", "session-1")
        assertTrue(state.players.any { it.name == "Josef" })
        assertEquals(1, state.players.size)
    }

    @Test
    fun `duplicate player is not added`() {
        controller.handleJoin("Josef", "session-1")
        val state = controller.handleJoin("Josef", "session-1")

        assertEquals(1, state.players.size)
    }

    @Test
    fun `game starts when two players join`() {
        controller.handleJoin("Josef", "session-1")
        val state = controller.handleJoin("Sebastian", "session-1")

        assertEquals(2, state.players.size)
        assertNotNull(state.currentTurn)
        // 3 regulaere Einheiten (ARCHER, INFANTRY, CAVALRY) + 1 BASE pro Spieler = 8 Units total.
        assertEquals(8, state.units.size)
        assertTrue(state.units.any { it.type == UnitType.ARCHER })
        assertTrue(state.units.any { it.type == UnitType.INFANTRY })
        assertTrue(state.units.any { it.type == UnitType.CAVALRY })
        assertTrue(state.units.any { it.type == UnitType.BASE })
        assertEquals(GameStatus.IN_PROGRESS, state.status)
    }

    @Test
    fun `third player cannot join`() {
        controller.handleJoin("Josef", "session-1")
        controller.handleJoin("Sebastian", "session-2")
        val state = controller.handleJoin("Gustav", "session-3")

        assertEquals(2, state.players.size)
    }

    @Test
    fun `move is rejected if game not started`() {
        val move = Move("Josef", UnitType.INFANTRY, 0, 0, 1, 1)

        val state = controller.handleMove(move)

        assertNull(state.currentTurn)
    }

    @Test
    fun `wrong player cannot move`() {
        controller.handleJoin("Josef", "session-1")
        controller.handleJoin("Sebastian", "session-2")

        val move = Move("Sebastian", UnitType.INFANTRY, 5, 5, 6, 6)

        val state = controller.handleMove(move)

        // Turn should still be Josef
        assertEquals("Josef", state.currentTurn)
    }

    @Test
    fun `player can move and turn switches`() {
        controller.handleJoin("Josef", "session-1")
        controller.handleJoin("Sebastian", "session-2")

        val move = Move("Josef", UnitType.INFANTRY, 3, 2, 3, 3)

        val state = controller.handleMove(move)

        val josefUnit = state.units.find {
            it.player == "Josef" && it.type == UnitType.INFANTRY
        }

        assertEquals(3, josefUnit?.x)
        assertEquals(3, josefUnit?.y)

        // Turn switched to Sebastian
        assertEquals("Sebastian", state.currentTurn)
    }

    @Test
    fun `move object is created correctly`() {
        val move = Move("Alice", UnitType.INFANTRY, 1, 1, 2, 2)

        assertEquals("Alice", move.player)
        assertEquals(UnitType.INFANTRY, move.type)
        assertEquals(1, move.fromX)
        assertEquals(2, move.toX)
    }

    @Test
    fun `game unit is initialized correctly`() {
        val unit = GameUnit("Alice", 2, 3, UnitType.INFANTRY)

        assertEquals("Alice", unit.player)
        assertEquals(2, unit.x)
        assertEquals(3, unit.y)
    }

    @Test
    fun `game state initializes empty`() {
        val state = GameState()

        assertTrue(state.players.isEmpty())
        assertTrue(state.units.isEmpty())
        assertNull(state.currentTurn)
    }

    @Test
    fun `multiple moves update unit positions correctly`() {
        controller.handleJoin("Alice", "session-1")
        controller.handleJoin("Bob", "session-2")

        // Gold geben, damit sie nach der Runde nicht pleitegehen
        gameService.getCurrentState().players.forEach { it.gold = 100 }

        // First move
        controller.handleMove(
            Move("Alice", UnitType.INFANTRY, 3, 2, 3, 3)
        )

        // Second move
        val result = controller.handleMove(
            Move("Bob", UnitType.INFANTRY, 6, 5, 6, 6)
        )

        val aliceUnit = result.units.find {
            it.player == "Alice" && it.type == UnitType.INFANTRY
        }

        val bobUnit = result.units.find {
            it.player == "Bob" && it.type == UnitType.INFANTRY
        }

        assertEquals(3, aliceUnit?.x)
        assertEquals(3, aliceUnit?.y)
        assertEquals(6, bobUnit?.x)
        assertEquals(6, bobUnit?.y)
    }

    @Test
    fun `move does nothing when wrong player`() {
        controller.handleJoin("Alice", "session-1")
        controller.handleJoin("Bob", "session-2")

        val result = controller.handleMove(Move("Bob", UnitType.INFANTRY, 5, 5, 7, 7))

        val bobUnit = result.units.find { it.player == "Bob" }

        // Position should NOT change
        assertEquals(5, bobUnit?.x)
        assertEquals(5, bobUnit?.y)
    }

    @Test
    fun `move ignored when game not started`() {
        val result = controller.handleMove(Move("Alice", UnitType.INFANTRY, 0, 0, 1, 1))

        assertTrue(result.units.isEmpty())
    }

    @Test
    fun `game stays waiting when only one player joins`() {
        val localHeaderAccessor = SimpMessageHeaderAccessor.create()

        val state = controller.join("Alice", localHeaderAccessor)!!

        assertEquals(1, state.players.size)
        assertEquals(GameStatus.WAITING_FOR_PLAYERS, state.status)
        assertNull(state.currentTurn)
        assertTrue(state.units.isEmpty())
    }

    @Test
    fun `move rejected when not players turn`() {
        controller.handleJoin("Alice", "session-1")
        controller.handleJoin("Bob", "session-2")

        val move = Move(player = "Bob", toX = 1, toY = 1)

        val state = controller.handleMove(move)

        assertEquals("Alice", state.currentTurn)
    }

    @Test
    fun `turn switches after valid move`() {
        controller.handleJoin("Alice", "session-1")
        controller.handleJoin("Bob", "session-2")

        // Gold geben, damit sie nach der Runde nicht pleitegehen
        gameService.getCurrentState().players.forEach { it.gold = 100 }

        // Alice move
        val state1 = controller.handleMove(
            Move("Alice", UnitType.INFANTRY, 3, 2, 3, 3)
        )
        assertEquals("Bob", state1.currentTurn)

        // Bob move
        val state2 = controller.handleMove(
            Move("Bob", UnitType.INFANTRY, 6, 5, 6, 6)
        )
        assertEquals("Alice", state2.currentTurn)
    }

    @Test
    fun `init returns current state`() {
        controller.handleJoin("Alice", "session-1")
        controller.handleJoin("Bob", "session-2")

        val state = controller.init()

        assertEquals(2, state.players.size)
    }

    @Test
    fun `join stores sessionId`() {
        val state = controller.handleJoin(
            "Alice",
            "session-1"
        )

        assertEquals(
            "session-1",
            state.players[0].sessionId
        )
    }

    @Test
    fun `join with empty sessionId still adds player`() {
        val state = gameService.handleJoin(
            "Alice",
            ""
        )

        assertEquals(1, state.players.size)
        assertEquals("Alice", state.players[0].name)
        assertEquals("", state.players[0].sessionId)
    }

    @Test
    fun `join uses empty sessionId when header sessionId is null`() {
        val localHeaderAccessor = mock(SimpMessageHeaderAccessor::class.java)

        `when`(localHeaderAccessor.sessionId).thenReturn(null)

        val state = controller.join(
            "Alice",
            localHeaderAccessor
        )!!

        assertEquals(
            "",
            state.players[0].sessionId
        )
    }

    @Test
    fun `player can join game with sessionId`() {
        val localHeaderAccessor = mock(SimpMessageHeaderAccessor::class.java)

        `when`(localHeaderAccessor.sessionId).thenReturn("session-1")

        val state = controller.join(
            "Josef",
            localHeaderAccessor
        )!!

        assertTrue(
            state.players.any { it.name == "Josef" }
        )
        assertEquals(1, state.players.size)
        assertEquals("session-1", state.players[0].sessionId)
    }

    @Test
    fun `join via websocket sends GAME_FULL error when full`() {
        controller.handleJoin("P1", "session-1")
        controller.handleJoin("P2", "session-2")

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
        controller.handleJoin("P1", "session-1")
        controller.handleJoin("P2", "session-2")

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
        controller.handleJoin("P1", "session-1")
        controller.handleJoin("P2", "session-2")

        val move = Move(player = "P1", fromX = 0, fromY = 0, toX = 9, toY = 9)
        val result = controller.move(move, headerAccessor)

        assertNull(result)
        verify(messagingTemplate).convertAndSendToUser(
            eq("test-session"),
            eq("/queue/errors"),
            argThat { it is ErrorMessage && it.errorCode == ErrorCode.INVALID_MOVE }
        )
    }

    @Test
    fun `move via websocket rejected when game status is FINISHED`() {
        controller.handleJoin("Alice", "session-1")
        controller.handleJoin("Bob", "session-2")

        gameService.gameState.status = GameStatus.FINISHED
    }

    @Test
    fun `broadcasts new state on success`() {
        controller.handleJoin("Alice", "session-1")
        controller.handleJoin("Bob", "session-2")

        val move = Move(
            player = "Alice",
            type = UnitType.INFANTRY,
            fromX = 3,
            fromY = 2,
            toX = 3,
            toY = 3
        )

        val result = controller.move(move, headerAccessor)

        assertNotNull(result)

        verifyNoInteractions(messagingTemplate)
    }

    @Test
    fun `valid move switches turn`() {
        controller.handleJoin("Alice", "session-1")
        controller.handleJoin("Bob", "session-2")

        val move = Move(
            player = "Alice",
            type = UnitType.INFANTRY,
            fromX = 3,
            fromY = 2,
            toX = 3,
            toY = 3
        )
        val result = controller.move(move, headerAccessor)

        assertNotNull(result)

        assertEquals("Bob", result?.currentTurn)
    }

    @Test
    fun `buyFarm via websocket returns updated state when gold is sufficient`() {
        controller.handleJoin("Alice", "test-session")
        controller.handleJoin("Bob", "session-2")

        val state = gameService.getCurrentState()
        val alice = state.players.first { it.name == "Alice" }
        alice.gold = 20 // Genug Gold

        // Endpoint aufrufen
        val result = controller.buyFarm(headerAccessor)

        // Verifizieren, dass der Kauf klappt und der neue State zurückkommt (@SendTo greift)
        assertNotNull(result)
        assertEquals(1, result?.players?.first { it.name == "Alice" }?.farms)
        assertEquals(8, result?.players?.first { it.name == "Alice" }?.gold)
    }

    @Test
    fun `buyFarm via websocket sends INSUFFICIENT_GOLD error when poor`() {
        controller.handleJoin("Alice", "test-session")
        controller.handleJoin("Bob", "session-2")

        val state = gameService.getCurrentState()
        val alice = state.players.first { it.name == "Alice" }
        alice.gold = 5 // Zu wenig Gold

        // Endpoint aufrufen
        val result = controller.buyFarm(headerAccessor)

        // Verifizieren, dass kein State gebroadcastet wird...
        assertNull(result)

        // ... sondern stattdessen exakt der Error an den User geschickt wird
        verify(messagingTemplate).convertAndSendToUser(
            eq("test-session"),
            eq("/queue/errors"),
            argThat { it is ErrorMessage && it.errorCode == ErrorCode.INSUFFICIENT_GOLD }
        )
    }

    @Test
    fun `buyFarm returns null if player session is unknown`() {
        // Leeres Spiel, niemand ist beigetreten -> headerAccessor hat test-session
        val result = controller.buyFarm(headerAccessor)

        // Da die Session unbekannt ist, muss der Controller direkt null zurückgeben
        assertNull(result)
    }

    @Test
    fun `buyFarm handles null sessionId from headerAccessor gracefully`() {
        // Simuliert, dass das Netzwerk keine Session-ID mitschickt
        val localHeaderAccessor = mock(SimpMessageHeaderAccessor::class.java)
        `when`(localHeaderAccessor.sessionId).thenReturn(null)

        val result = controller.buyFarm(localHeaderAccessor)

        // Da die Session null (bzw. "") ist, wird kein Spieler gefunden -> null
        assertNull(result)
    }

    @Test
    fun `join allows reconnecting player even if game is max capacity`() {
        // Spiel ist voll mit Alice und Bob
        controller.handleJoin("Alice", "session-1")
        controller.handleJoin("Bob", "session-2")

        // Alice verliert die Verbindung und joint neu mit neuer Session-ID
        val localHeaderAccessor = mock(SimpMessageHeaderAccessor::class.java)
        `when`(localHeaderAccessor.sessionId).thenReturn("session-3")

        val state = controller.join("Alice", localHeaderAccessor)

        // Sie darf rein, weil sie schon Teil des Spiels ist (kein GAME_FULL Error)
        assertNotNull(state)
        assertEquals(2, state?.players?.size)
    }
}