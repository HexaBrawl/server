package at.aau.hexabrawl.websocketserver

import at.aau.hexabrawl.websocketserver.websocket.broker.GameState
import at.aau.hexabrawl.websocketserver.websocket.broker.GameUnit
import at.aau.hexabrawl.websocketserver.websocket.broker.Move
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GameStateTest {

    private lateinit var gameState: GameState

    @BeforeEach
    fun setUp() {
        gameState = GameState()
    }

    @Test
    fun `game state is empty on start`() {
        assertEquals(0, gameState.players.size)
        assertEquals(0, gameState.units.size)
        assertNull(gameState.currentTurn)
    }

    @Test
    fun `adding one player increases player count to 1`() {
        gameState.players.add("Alice")

        assertEquals(1, gameState.players.size)
    }

    @Test
    fun `adding two players increases player count to 2`() {
        gameState.players.add("Alice")
        gameState.players.add("Bob")

        assertEquals(2, gameState.players.size)
    }

    @Test
    fun `added player is contained in player list`() {
        gameState.players.add("Alice")

        assertTrue(gameState.players.contains("Alice"))
    }

    @Test
    fun `current turn can be set`() {
        gameState.currentTurn = "Alice"

        assertEquals("Alice", gameState.currentTurn)
    }

    @Test
    fun `game unit can be added to game state`() {
        val unit = GameUnit(player = "Alice", x = 2, y = 3)
        gameState.units.add(unit)

        assertEquals(1, gameState.units.size)
    }

    @Test
    fun `game unit has correct coordinates`() {
        val unit = GameUnit(player = "Alice", x = 4, y = 7)

        assertEquals(4, unit.x)
        assertEquals(7, unit.y)
    }

    @Test
    fun `move can be created with all fields`() {
        val move = Move(player = "Alice", type = "MOVE", fromX = 1, fromY = 2, toX = 3, toY = 4)

        assertEquals("Alice", move.player)
        assertEquals("MOVE", move.type)
        assertEquals(1, move.fromX)
        assertEquals(2, move.fromY)
        assertEquals(3, move.toX)
        assertEquals(4, move.toY)
    }

    @Test
    fun `move has correct default values`() {
        val move = Move()

        assertEquals("", move.player)
        assertEquals("", move.type)
        assertEquals(0, move.fromX)
        assertEquals(0, move.fromY)
        assertEquals(0, move.toX)
        assertEquals(0, move.toY)
    }
}