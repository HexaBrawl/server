package at.aau.hexabrawl.websocketserver

import at.aau.hexabrawl.websocketserver.websocket.broker.GameState
import at.aau.hexabrawl.websocketserver.websocket.broker.GameUnit
import at.aau.hexabrawl.websocketserver.websocket.broker.Move
import at.aau.hexabrawl.websocketserver.websocket.broker.WebSocketBrokerController
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test


class WebSocketBrokerControllerTest {

    private lateinit var controller: WebSocketBrokerController

    @BeforeEach
    fun setup() {
        controller = WebSocketBrokerController()
    }

    @Test
    fun `player can join game`() {
        val state = controller.handleJoin("Josef")

        Assertions.assertTrue(state.players.contains("Josef"))
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
        Assertions.assertEquals(2, state.units.size)
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
        val move = Move("Josef", "MOVE", 0, 0, 1, 1)

        val state = controller.handleMove(move)

        Assertions.assertNull(state.currentTurn)
    }

    @Test
    fun `wrong player cannot move`() {
        controller.handleJoin("Josef")
        controller.handleJoin("Sebastian")

        val move = Move("Sebastian", "MOVE", 5, 5, 6, 6)

        val state = controller.handleMove(move)

        // Turn should still be Josef
        Assertions.assertEquals("Josef", state.currentTurn)
    }

    @Test
    fun `player can move and turn switches`() {
        controller.handleJoin("Josef")
        controller.handleJoin("Sebastian")

        val move = Move("Josef", "MOVE", 2, 2, 3, 3)

        val state = controller.handleMove(move)

        val unit = state.units.find { it.player == "Josef" }

        Assertions.assertEquals(3, unit?.x)
        Assertions.assertEquals(3, unit?.y)

        // Turn switched to Sebastian
        Assertions.assertEquals("Sebastian", state.currentTurn)
    }

    @Test
    fun `move object is created correctly`() {
        val move = Move("Alice", "MOVE", 1, 1, 2, 2)

        assertEquals("Alice", move.player)
        assertEquals("MOVE", move.type)
        assertEquals(1, move.fromX)
        assertEquals(2, move.toX)
    }

    @Test
    fun `game unit is initialized correctly`() {
        val unit = GameUnit("Alice", 2, 3)

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
        val controller = WebSocketBrokerController()

        controller.handleJoin("Alice")
        controller.handleJoin("Bob")

        // First move
        controller.handleMove(Move("Alice", "MOVE", 2, 2, 3, 3))

        // Second move
        val result = controller.handleMove(Move("Bob", "MOVE", 5, 5, 6, 6))

        val aliceUnit = result.units.find { it.player == "Alice" }
        val bobUnit = result.units.find { it.player == "Bob" }

        assertEquals(3, aliceUnit?.x)
        assertEquals(3, aliceUnit?.y)
        assertEquals(6, bobUnit?.x)
        assertEquals(6, bobUnit?.y)
    }

    @Test
    fun `move does nothing when wrong player`() {
        val controller = WebSocketBrokerController()

        controller.handleJoin("Alice")
        controller.handleJoin("Bob")

        val result = controller.handleMove(Move("Bob", "MOVE", 5, 5, 7, 7))

        val bobUnit = result.units.find { it.player == "Bob" }

        // Position should NOT change
        assertEquals(5, bobUnit?.x)
        assertEquals(5, bobUnit?.y)
    }

    @Test
    fun `move ignored when game not started`() {
        val controller = WebSocketBrokerController()

        val result = controller.handleMove(Move("Alice", "MOVE", 0, 0, 1, 1))

        assertTrue(result.units.isEmpty())
    }
}