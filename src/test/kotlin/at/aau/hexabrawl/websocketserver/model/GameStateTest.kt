package at.aau.hexabrawl.websocketserver.model

import org.junit.jupiter.api.Assertions
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
        Assertions.assertEquals(0, gameState.players.size)
        Assertions.assertEquals(0, gameState.units.size)
        Assertions.assertNull(gameState.currentTurn)
        Assertions.assertEquals(GameStatus.WAITING_FOR_PLAYERS, gameState.status)
    }

    @Test
    fun `adding one player increases player count to 1`() {
        gameState.players.add(Player("Alice"))

        Assertions.assertEquals(1, gameState.players.size)
    }

    @Test
    fun `adding two players increases player count to 2`() {
        gameState.players.add(Player("Alice"))
        gameState.players.add(Player("Bob"))

        Assertions.assertEquals(2, gameState.players.size)
    }

    @Test
    fun `added player is contained in player list`() {
        gameState.players.add(Player("Alice"))

        Assertions.assertTrue(gameState.players.any { it.name == "Alice" })
    }

    @Test
    fun `current turn can be set`() {
        gameState.currentTurn = "Alice"

        Assertions.assertEquals("Alice", gameState.currentTurn)
    }

    @Test
    fun `game unit can be added to game state`() {
        val unit = GameUnit(player = "Alice", x = 2, y = 3)
        gameState.units.add(unit)

        Assertions.assertEquals(1, gameState.units.size)
    }

    @Test
    fun `game unit has correct coordinates`() {
        val unit = GameUnit(player = "Alice", x = 4, y = 7)

        Assertions.assertEquals(4, unit.x)
        Assertions.assertEquals(7, unit.y)
    }

    @Test
    fun `move can be created with all fields`() {
        val move = Move(player = "Alice", type = "MOVE", fromX = 1, fromY = 2, toX = 3, toY = 4)

        Assertions.assertEquals("Alice", move.player)
        Assertions.assertEquals("MOVE", move.type)
        Assertions.assertEquals(1, move.fromX)
        Assertions.assertEquals(2, move.fromY)
        Assertions.assertEquals(3, move.toX)
        Assertions.assertEquals(4, move.toY)
    }

    @Test
    fun `move has correct default values`() {
        val move = Move()

        Assertions.assertEquals("", move.player)
        Assertions.assertEquals("", move.type)
        Assertions.assertEquals(0, move.fromX)
        Assertions.assertEquals(0, move.fromY)
        Assertions.assertEquals(0, move.toX)
        Assertions.assertEquals(0, move.toY)
    }

    @Test
    fun `game state starts with status WAITING_FOR_PLAYERS`() {
        Assertions.assertEquals(GameStatus.WAITING_FOR_PLAYERS, gameState.status)
    }


}