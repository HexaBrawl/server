package at.aau.hexabrawl.websocketserver.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DisconnectHandlerTest {

    private lateinit var gameService: GameService

    @BeforeEach
    fun setUp() {
        gameService = GameService(CombatService())
    }

    @Test
    fun `disconnect during WAITING_FOR_PLAYERS removes player`() {
        gameService.handleJoin("Alice", "session-1")
        gameService.handleDisconnect("session-1")

        assertEquals(0, gameService.gameState.players.size)
        assertEquals(GameStatus.WAITING_FOR_PLAYERS, gameService.gameState.status)
    }

    @Test
    fun `disconnect during IN_PROGRESS sets status to FINISHED`() {
        gameService.handleJoin("Alice", "session-1")
        gameService.handleJoin("Bob", "session-2")

        assertEquals(GameStatus.IN_PROGRESS, gameService.gameState.status)

        gameService.handleDisconnect("session-1")

        assertEquals(GameStatus.FINISHED, gameService.gameState.status)
    }

    @Test
    fun `disconnect removes player units`() {
        gameService.handleJoin("Alice", "session-1")
        gameService.handleJoin("Bob", "session-2")

        gameService.handleDisconnect("session-1")

        val aliceUnits = gameService.gameState.units.filter { it.player == "Alice" }
        assertEquals(0, aliceUnits.size)
    }

    @Test
    fun `disconnect with unknown sessionId does nothing`() {
        gameService.handleJoin("Alice", "session-1")
        gameService.handleDisconnect("unknown-session")

        assertEquals(1, gameService.gameState.players.size)
    }

    @Test
    fun `disconnect sets currentTurn to null when IN_PROGRESS`() {
        gameService.handleJoin("Alice", "session-1")
        gameService.handleJoin("Bob", "session-2")

        gameService.handleDisconnect("session-1")

        assertNull(gameService.gameState.currentTurn)
    }

    @Test
    fun `disconnect only removes disconnected player units`() {
        gameService.handleJoin("Alice", "session-1")
        gameService.handleJoin("Bob", "session-2")

        gameService.handleDisconnect("session-1")

        // 3 regulaere Einheiten (ARCHER, INFANTRY, CAVALRY) + 1 BASE pro Spieler.
        val bobUnits = gameService.gameState.units.filter { it.player == "Bob" }
        assertEquals(4, bobUnits.size)
    }

    @Test
    fun `disconnect when opponent still has units declares opponent as winner`() {
        gameService.handleJoin("Alice", "session-1")
        gameService.handleJoin("Bob", "session-2")

        gameService.handleDisconnect("session-1")

        val state = gameService.gameState
        assertEquals(GameStatus.FINISHED, state.status)
        assertEquals("Bob", state.winner)
        assertNull(state.currentTurn)
    }

    @Test
    fun `disconnect when no player has units left ends as draw`() {
        gameService.handleJoin("Alice", "session-1")
        gameService.handleJoin("Bob", "session-2")

        // Bobs Units entfernen, damit nach Alice's Disconnect niemand mehr
        // Einheiten auf dem Brett hat -> Unentschieden
        gameService.gameState.units.removeIf { it.player == "Bob" }

        gameService.handleDisconnect("session-1")

        val state = gameService.gameState
        assertEquals(GameStatus.FINISHED, state.status)
        assertNull(state.winner)
        assertNull(state.currentTurn)
    }

}