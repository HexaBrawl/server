package at.aau.hexabrawl.websocketserver.model

import org.junit.jupiter.api.Assertions.*
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

        assertEquals("Alice", move.player)
        assertEquals(UnitType.INFANTRY, move.type)
        assertEquals(1, move.fromX)
        assertEquals(2, move.fromY)
        assertEquals(3, move.toX)
        assertEquals(4, move.toY)
    }

    @Test
    fun `move default constructor coverage`() {
        val move = Move()

        assertEquals("", move.player)
        assertEquals(UnitType.INFANTRY, move.type)
        assertEquals(0, move.fromX)
        assertEquals(0, move.fromY)
        assertEquals(0, move.toX)
        assertEquals(0, move.toY)
    }

    @Test
    fun `game unit covers all properties`() {
        val unit = GameUnit("Bob", 5, 6, UnitType.INFANTRY)

        assertEquals("Bob", unit.player)
        assertEquals(5, unit.x)
        assertEquals(6, unit.y)
    }

    @Test
    fun `game unit constructor`() {
        val unit = GameUnit("", 0, 0, UnitType.INFANTRY)

        assertEquals("", unit.player)
        assertEquals(0, unit.x)
        assertEquals(0, unit.y)
        assertEquals(UnitType.INFANTRY, unit.type)
    }

    @Test
    fun `game state mutation coverage`() {
        val state = GameState()
        state.players.add(Player("Alice"))
        state.units.add(GameUnit("Alice", 1, 1, UnitType.INFANTRY))
        state.currentTurn = "Alice"

        assertEquals(1, state.players.size)
        assertEquals(1, state.units.size)
        assertEquals("Alice", state.currentTurn)
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
        assertEquals(ErrorCode.GAME_FULL, error.errorCode)
        assertEquals("Spiel ist voll", error.message)
    }

    @Test
    fun `StompMessage coverage`() {
        val msg = StompMessage("Server", "Test-Inhalt")
        assertEquals("Server", msg.from)
        assertEquals("Test-Inhalt", msg.text)
    }

    @Test
    fun `handleJoin only modifies provided state`() {

        val gameService = GameService(CombatService())

        val state1 = GameState()
        val state2 = GameState()

        gameService.handleJoin(
            state1,
            "Josef",
            "session-1"
        )

        assertEquals(1, state1.players.size)
        assertEquals(0, state2.players.size)

        assertEquals(
            "Josef",
            state1.players[0].name
        )
    }

    @Test
    fun `legacy handleJoin bridge still uses gameState`() {

        val gameService = GameService(CombatService())

        gameService.handleJoin(
            "Josef",
            "session-1"
        )

        assertEquals(
            1,
            gameService.gameState.players.size
        )

        assertEquals(
            "Josef",
            gameService.gameState.players[0].name
        )
    }

    @Test
    fun `handleMove only modifies provided state`() {
        val gameService = GameService(CombatService())

        val state1 = GameState()
        val state2 = GameState()

        gameService.handleJoin(state1, "Josef", "s1")
        gameService.handleJoin(state1, "Marie", "s2")

        val move = Move(
            "Josef",
            UnitType.INFANTRY,
            3,
            2,
            3,
            3
        )

        gameService.handleMove(state1, move)

        val movedUnit =
            state1.units.find {
                it.player == "Josef" &&
                        it.type == UnitType.INFANTRY
            }

        assertEquals(3, movedUnit?.x)
        assertEquals(3, movedUnit?.y)

        assertTrue(state2.units.isEmpty())
    }

    @Test
    fun `legacy handleMove bridge still uses gameState`() {
        val gameService = GameService(CombatService())

        gameService.handleJoin("Josef", "s1")
        gameService.handleJoin("Marie", "s2")

        val move = Move(
            "Josef",
            UnitType.INFANTRY,
            3,
            2,
            3,
            3
        )

        gameService.handleMove(move)

        val movedUnit =
            gameService.gameState.units.find {
                it.player == "Josef" &&
                        it.type == UnitType.INFANTRY
            }

        assertEquals(3, movedUnit?.x)
        assertEquals(3, movedUnit?.y)
    }

    @Test
    fun `handleDisconnect only modifies provided state`() {

        val gameService = GameService(CombatService())

        val state1 = GameState()
        val state2 = GameState()

        gameService.handleJoin(
            state1,
            "Josef",
            "s1"
        )

        gameService.handleDisconnect(
            state1,
            "s1"
        )

        assertTrue(state1.players.isEmpty())
        assertTrue(state2.players.isEmpty())

        assertEquals(
            GameStatus.WAITING_FOR_PLAYERS,
            state2.status
        )
    }

    @Test
    fun `legacy handleDisconnect bridge still uses gameState`() {

        val gameService = GameService(CombatService())

        gameService.handleJoin(
            "Josef",
            "s1"
        )

        gameService.handleDisconnect("s1")

        assertTrue(
            gameService.gameState.players.isEmpty()
        )
    }

    @Test
    fun `initializeGame only modifies provided state`() {

        val gameService = GameService(CombatService())

        val state1 = GameState()
        val state2 = GameState()

        gameService.handleJoin(state1, "Josef", "s1")

        gameService.initializeGame(state1)

        assertTrue(state1.players.isEmpty())

        assertTrue(state2.players.isEmpty())

        assertTrue(state2.units.isEmpty())
    }

    @Test
    fun `resetToStartCondition only modifies provided state`() {

        val gameService = GameService(CombatService())

        val state1 = GameState()
        val state2 = GameState()

        gameService.handleJoin(state1, "Josef", "s1")
        gameService.handleJoin(state1, "Marie", "s2")

        gameService.resetToStartCondition(state1)

        // Spieler bleiben erhalten
        assertEquals(2, state1.players.size)

        // Units existieren weiterhin
        assertFalse(state1.units.isEmpty())

        // Zweiter State bleibt unverändert
        assertTrue(state2.players.isEmpty())
        assertTrue(state2.units.isEmpty())
    }

    @Test
    fun `getCurrentState returns provided state`() {

        val gameService = GameService(CombatService())

        val state1 = GameState()

        val result = gameService.getCurrentState(state1)

        assertSame(state1, result)
    }

}