package at.aau.hexabrawl.websocketserver.model

import org.junit.jupiter.api.Assertions
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

        Assertions.assertEquals("Alice", move.player)
        Assertions.assertEquals("MOVE", move.type)
        Assertions.assertEquals(1, move.fromX)
        Assertions.assertEquals(2, move.fromY)
        Assertions.assertEquals(3, move.toX)
        Assertions.assertEquals(4, move.toY)
    }

    @Test
    fun `move default constructor coverage`() {
        val move = Move()

        Assertions.assertEquals("", move.player)
        Assertions.assertEquals("", move.type)
        Assertions.assertEquals(0, move.fromX)
        Assertions.assertEquals(0, move.fromY)
        Assertions.assertEquals(0, move.toX)
        Assertions.assertEquals(0, move.toY)
    }

    @Test
    fun `game unit covers all properties`() {
        val unit = GameUnit("Bob", 5, 6)

        Assertions.assertEquals("Bob", unit.player)
        Assertions.assertEquals(5, unit.x)
        Assertions.assertEquals(6, unit.y)
    }

    @Test
    fun `game unit default constructor`() {
        val unit = GameUnit("")

        Assertions.assertEquals("", unit.player)
        Assertions.assertEquals(0, unit.x)
        Assertions.assertEquals(0, unit.y)
    }

    @Test
    fun `game state mutation coverage`() {
        val state = GameState()

        state.players.add(Player("Alice"))
        state.units.add(GameUnit("Alice", 1, 1))
        state.currentTurn = "Alice"

        Assertions.assertEquals(1, state.players.size)
        Assertions.assertEquals(1, state.units.size)
        Assertions.assertEquals("Alice", state.currentTurn)
    }
}