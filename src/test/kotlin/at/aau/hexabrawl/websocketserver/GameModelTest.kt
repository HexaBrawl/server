package at.aau.hexabrawl.websocketserver

import at.aau.hexabrawl.websocketserver.websocket.broker.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GameModelTest {

    @Test
    fun `move covers all properties`() {
        val move = Move(
            player = "Alice",
            type = "MOVE",
            fromX = 1,
            fromY = 2,
            toX = 3,
            toY = 4
        )

        assertEquals("Alice", move.player)
        assertEquals("MOVE", move.type)
        assertEquals(1, move.fromX)
        assertEquals(2, move.fromY)
        assertEquals(3, move.toX)
        assertEquals(4, move.toY)
    }

    @Test
    fun `move default constructor coverage`() {
        val move = Move()

        assertEquals("", move.player)
        assertEquals("", move.type)
        assertEquals(0, move.fromX)
        assertEquals(0, move.fromY)
        assertEquals(0, move.toX)
        assertEquals(0, move.toY)
    }

    @Test
    fun `game unit covers all properties`() {
        val unit = GameUnit("Bob", 5, 6)

        assertEquals("Bob", unit.player)
        assertEquals(5, unit.x)
        assertEquals(6, unit.y)
    }

    @Test
    fun `game unit default constructor`() {
        val unit = GameUnit()

        assertEquals("", unit.player)
        assertEquals(0, unit.x)
        assertEquals(0, unit.y)
    }

    @Test
    fun `game state mutation coverage`() {
        val state = GameState()

        state.players.add("Alice")
        state.units.add(GameUnit("Alice", 1, 1))
        state.currentTurn = "Alice"

        assertEquals(1, state.players.size)
        assertEquals(1, state.units.size)
        assertEquals("Alice", state.currentTurn)
    }
}