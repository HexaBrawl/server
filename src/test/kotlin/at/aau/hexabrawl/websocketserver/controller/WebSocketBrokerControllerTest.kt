package at.aau.hexabrawl.websocketserver.controller


import at.aau.hexabrawl.websocketserver.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.springframework.messaging.simp.SimpMessageHeaderAccessor

class WebSocketBrokerControllerTest {

    private lateinit var controller: WebSocketBrokerController
    private lateinit var gameService: GameService

    @BeforeEach
    fun setup() {
        gameService = GameService(CombatService())
        controller = WebSocketBrokerController(gameService)
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
        assertEquals(6, state.units.size)
        assertTrue(state.units.any { it.type == UnitType.ARCHER })
        assertTrue(state.units.any { it.type == UnitType.INFANTRY })
        assertTrue(state.units.any { it.type == UnitType.CAVALRY })
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
        val controller = WebSocketBrokerController(gameService)

        controller.handleJoin("Alice", "session-1")
        controller.handleJoin("Bob", "session-2")

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
        val controller = WebSocketBrokerController(gameService)

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
        val controller = WebSocketBrokerController(gameService)

        val result = controller.handleMove(Move("Alice", UnitType.INFANTRY, 0, 0, 1, 1))

        assertTrue(result.units.isEmpty())
    }

    @Test
    fun `game stays waiting when only one player joins`() {
        val controller = WebSocketBrokerController(gameService)
        val headerAccessor = SimpMessageHeaderAccessor.create()

        val state = controller.join("Alice", headerAccessor)

        assertEquals(1, state.players.size)
        assertEquals(GameStatus.WAITING_FOR_PLAYERS, state.status)
        assertNull(state.currentTurn)
        assertTrue(state.units.isEmpty())
    }

    @Test
    fun `move rejected when not players turn`() {
        val controller = WebSocketBrokerController(gameService)

        controller.handleJoin("Alice", "session-1")
        controller.handleJoin("Bob", "session-2")

        val move = Move(player = "Bob", toX = 1, toY = 1)

        val state = controller.handleMove(move)

        assertEquals("Alice", state.currentTurn)
    }

    @Test
    fun `turn switches after valid move`() {
        val controller = WebSocketBrokerController(gameService)

        controller.handleJoin("Alice", "session-1")
        controller.handleJoin("Bob", "session-2")

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
        val controller = WebSocketBrokerController(gameService)

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

        val headerAccessor =
            mock(SimpMessageHeaderAccessor::class.java)

        Mockito.`when`(
            headerAccessor.sessionId
        ).thenReturn(null)

        val state =
            controller.join(
                "Alice",
                headerAccessor
            )

        assertEquals(
            "",
            state.players[0].sessionId
        )
    }

    @Test
    fun `player can join game with sessionId`() {

        val headerAccessor =
            Mockito.mock(SimpMessageHeaderAccessor::class.java)

        Mockito.`when`(
            headerAccessor.sessionId
        ).thenReturn("session-1")

        val state = controller.join(
            "Josef",
            headerAccessor
        )

        assertTrue(
            state.players.any { it.name == "Josef" }
        )

        assertEquals(
            1,
            state.players.size
        )

        assertEquals(
            "session-1",
            state.players[0].sessionId
        )
    }
}