package at.aau.hexabrawl.websocketserver.model

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GameModelTest {

    @Test
    fun `move covers all properties`() {
        val move = Move(
            player = "Alice",
            type = UnitType.INFANTRY,
            fromX = 1,
            fromY = 2,
            toX = 3,
            toY = 4
        )

        Assertions.assertEquals("Alice", move.player)
        Assertions.assertEquals(UnitType.INFANTRY, move.type)
        Assertions.assertEquals(1, move.fromX)
        Assertions.assertEquals(2, move.fromY)
        Assertions.assertEquals(3, move.toX)
        Assertions.assertEquals(4, move.toY)
    }

    @Test
    fun `move default constructor coverage`() {
        val move = Move()

        Assertions.assertEquals("", move.player)
        Assertions.assertEquals(UnitType.INFANTRY, move.type)
        Assertions.assertEquals(0, move.fromX)
        Assertions.assertEquals(0, move.fromY)
        Assertions.assertEquals(0, move.toX)
        Assertions.assertEquals(0, move.toY)
    }

    @Test
    fun `game unit covers all properties`() {
        val unit = GameUnit("Bob", 5, 6, UnitType.INFANTRY)

        Assertions.assertEquals("Bob", unit.player)
        Assertions.assertEquals(5, unit.x)
        Assertions.assertEquals(6, unit.y)
    }

    @Test
    fun `game unit constructor`() {
        val unit = GameUnit("", 0, 0, UnitType.INFANTRY)

        Assertions.assertEquals("", unit.player)
        Assertions.assertEquals(0, unit.x)
        Assertions.assertEquals(0, unit.y)
        Assertions.assertEquals(UnitType.INFANTRY, unit.type)
    }

    @Test
    fun `game state mutation coverage`() {
        val state = GameState()
        state.players.add(Player("Alice"))
        state.units.add(GameUnit("Alice", 1, 1, UnitType.INFANTRY))
        state.currentTurn = "Alice"

        Assertions.assertEquals(1, state.players.size)
        Assertions.assertEquals(1, state.units.size)
        Assertions.assertEquals("Alice", state.currentTurn)
    }

    @Test
    fun `test ErrorMessage and ErrorCode structure`() {
        val message = ErrorMessage(ErrorCode.INVALID_MOVE, "Test Fehler")

        assertEquals(ErrorCode.INVALID_MOVE, message.errorCode)
        assertEquals("Test Fehler", message.message)
    }

    @Test
    fun `ErrorMessage and ErrorCode coverage`() {
        val error = ErrorMessage(ErrorCode.GAME_FULL, "Spiel ist voll")
        Assertions.assertEquals(ErrorCode.GAME_FULL, error.errorCode)
        Assertions.assertEquals("Spiel ist voll", error.message)
    }

    @Test
    fun `StompMessage coverage`() {
        val msg = StompMessage("Server", "Test-Inhalt")
        Assertions.assertEquals("Server", msg.from)
        Assertions.assertEquals("Test-Inhalt", msg.text)
    }

    @Test
    fun `handleJoin only modifies provided state`() {

        val gameService = GameService(CombatService())

        val state1 = GameState()
        val state2 = GameState()

        gameService.handleJoin(
            state1,
            "Alice",
            "session-1"
        )

        assertEquals(1, state1.players.size)
        assertEquals(0, state2.players.size)

        assertEquals(
            "Alice",
            state1.players[0].name
        )
    }

    @Test
    fun `legacy handleJoin bridge still uses gameState`() {

        val gameService = GameService(CombatService())

        gameService.handleJoin(
            "Alice",
            "session-1"
        )

        assertEquals(
            1,
            gameService.gameState.players.size
        )

        assertEquals(
            "Alice",
            gameService.gameState.players[0].name
        )
    }
}