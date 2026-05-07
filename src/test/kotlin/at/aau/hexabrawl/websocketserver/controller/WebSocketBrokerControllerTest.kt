package at.aau.hexabrawl.websocketserver.controller


import at.aau.hexabrawl.websocketserver.model.GameService
import at.aau.hexabrawl.websocketserver.model.*
import at.aau.hexabrawl.websocketserver.controller.WebSocketBrokerController
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WebSocketBrokerControllerTest {

    private lateinit var controller: WebSocketBrokerController
    private lateinit var gameService: GameService

    @BeforeEach
    fun setup() {

        gameService = GameService()
        controller = WebSocketBrokerController(gameService)
    }

    @Test
    fun `player can join game`() {
        val state = controller.handleJoin("Josef")

        Assertions.assertTrue(state.players.any { it.name == "Josef" })
        Assertions.assertEquals(1, state.players.size)
    }

    @Test
    fun `duplicate player is not added`() {
        controller.handleJoin("Josef")
        val state = controller.handleJoin("Josef")

        Assertions.assertEquals(1, state.players.size)
    }

    @Test
    fun `game starts when two players join`() {
        controller.handleJoin("Josef")
        val state = controller.handleJoin("Sebastian")

        Assertions.assertEquals(2, state.players.size)
        Assertions.assertNotNull(state.currentTurn)
        Assertions.assertEquals(6, state.units.size)
        assertTrue(state.units.any { it.type == UnitType.ARCHER })
        assertTrue(state.units.any { it.type == UnitType.INFANTRY })
        assertTrue(state.units.any { it.type == UnitType.CAVALRY })
        Assertions.assertEquals(GameStatus.IN_PROGRESS, state.status)
    }

    @Test
    fun `third player cannot join`() {
        controller.handleJoin("Josef")
        controller.handleJoin("Sebastian")
        val state = controller.handleJoin("Gustav")

        Assertions.assertEquals(2, state.players.size)
    }

    @Test
    fun `move is rejected if game not started`() {
        val move = Move("Josef", UnitType.INFANTRY, 0, 0, 1, 1)

        val state = controller.handleMove(move)

        Assertions.assertNull(state.currentTurn)
    }

    @Test
    fun `wrong player cannot move`() {
        controller.handleJoin("Josef")
        controller.handleJoin("Sebastian")

        val move = Move("Sebastian", UnitType.INFANTRY, 5, 5, 6, 6)

        val state = controller.handleMove(move)

        // Turn should still be Josef
        Assertions.assertEquals("Josef", state.currentTurn)
    }

    @Test
    fun `player can move and turn switches`() {
        controller.handleJoin("Josef")
        controller.handleJoin("Sebastian")

        val move = Move("Josef", UnitType.INFANTRY, 3, 2, 3, 3)

        val state = controller.handleMove(move)


        val josefUnit = state.units.find {
            it.player == "Josef" && it.type == UnitType.INFANTRY
        }

        Assertions.assertEquals(3, josefUnit?.x)
        Assertions.assertEquals(3, josefUnit?.y)

        // Turn switched to Sebastian
        Assertions.assertEquals("Sebastian", state.currentTurn)
    }

    @Test
    fun `move object is created correctly`() {
        val move = Move("Alice", UnitType.INFANTRY, 1, 1, 2, 2)

        Assertions.assertEquals("Alice", move.player)
        Assertions.assertEquals(UnitType.INFANTRY, move.type)
        Assertions.assertEquals(1, move.fromX)
        Assertions.assertEquals(2, move.toX)
    }

    @Test
    fun `game unit is initialized correctly`() {
        val unit = GameUnit("Alice", 2, 3, UnitType.INFANTRY)

        Assertions.assertEquals("Alice", unit.player)
        Assertions.assertEquals(2, unit.x)
        Assertions.assertEquals(3, unit.y)
    }

    @Test
    fun `game state initializes empty`() {
        val state = GameState()

        Assertions.assertTrue(state.players.isEmpty())
        Assertions.assertTrue(state.units.isEmpty())
        Assertions.assertNull(state.currentTurn)
    }

    @Test
    fun `multiple moves update unit positions correctly`() {
        val controller = WebSocketBrokerController(gameService)

        controller.handleJoin("Alice")
        controller.handleJoin("Bob")

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

        Assertions.assertEquals(3, aliceUnit?.x)
        Assertions.assertEquals(3, aliceUnit?.y)
        Assertions.assertEquals(6, bobUnit?.x)
        Assertions.assertEquals(6, bobUnit?.y)
    }

    @Test
    fun `move does nothing when wrong player`() {
        val controller = WebSocketBrokerController(gameService)

        controller.handleJoin("Alice")
        controller.handleJoin("Bob")

        val result = controller.handleMove(Move("Bob", UnitType.INFANTRY, 5, 5, 7, 7))

        val bobUnit = result.units.find { it.player == "Bob" }

        // Position should NOT change
        Assertions.assertEquals(5, bobUnit?.x)
        Assertions.assertEquals(5, bobUnit?.y)
    }

    @Test
    fun `move ignored when game not started`() {
        val controller = WebSocketBrokerController(gameService)

        val result = controller.handleMove(Move("Alice", UnitType.INFANTRY, 0, 0, 1, 1))

        Assertions.assertTrue(result.units.isEmpty())
    }

    @Test
    fun `game stays waiting when only one player joins`() {
        val controller = WebSocketBrokerController(gameService)

        val state = controller.join("Alice")

        Assertions.assertEquals(1, state.players.size)
        Assertions.assertEquals(GameStatus.WAITING_FOR_PLAYERS, state.status)
        Assertions.assertNull(state.currentTurn)
        Assertions.assertTrue(state.units.isEmpty())
    }

    @Test
    fun `move rejected when not players turn`() {
        val controller = WebSocketBrokerController(gameService)

        controller.handleJoin("Alice")
        controller.handleJoin("Bob")

        val move = Move(player = "Bob", toX = 1, toY = 1)

        val state = controller.handleMove(move)

        Assertions.assertEquals("Alice", state.currentTurn)
    }

    @Test
    fun `turn switches after valid move`() {
        val controller = WebSocketBrokerController(gameService)

        controller.handleJoin("Alice")
        controller.handleJoin("Bob")

        // Alice move
        val state1 = controller.handleMove(
            Move("Alice", UnitType.INFANTRY, 3, 2, 3, 3)
        )
        Assertions.assertEquals("Bob", state1.currentTurn)

        // Bob move
        val state2 = controller.handleMove(
            Move("Bob", UnitType.INFANTRY, 6, 5, 6, 6)
        )
        Assertions.assertEquals("Alice", state2.currentTurn)
    }

    @Test
    fun `init returns current state`() {
        val controller = WebSocketBrokerController(gameService)

        controller.handleJoin("Alice")
        controller.handleJoin("Bob")

        val state = controller.init()

        assertEquals(2, state.players.size)
    }
}