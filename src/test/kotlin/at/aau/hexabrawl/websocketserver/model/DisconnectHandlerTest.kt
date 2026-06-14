package at.aau.hexabrawl.websocketserver.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import at.aau.hexabrawl.websocketserver.TestServiceFactory

class DisconnectHandlerTest {

    private lateinit var gameService: GameService

    @BeforeEach
    fun setUp() {
        gameService = TestServiceFactory.createGameService()
    }

    /**
     * Test-Helper: simuliert die kombinierte Soft+Hard-Disconnect-Sequenz
     * (handleDisconnect markiert, hardDelete macht endgueltig). Im echten
     * Code passiert das durch den Scheduled Cleanup nach 30s Grace.
     */
    private fun forceDisconnect(sessionId: String) {
        val state = gameService.gameState
        val player = state.players.find { it.sessionId == sessionId } ?: return
        gameService.handleDisconnect(sessionId)
        gameService.hardDelete(state, player)
    }

    @Test
    fun `handleDisconnect alone only marks player as not connected`() {
        gameService.handleJoin("Alice", "session-1")
        gameService.handleJoin("Bob", "session-2")

        gameService.handleDisconnect("session-1")

        val alice = gameService.gameState.players.first { it.name == "Alice" }
        assertFalse(alice.connected)
        assertNotNull(alice.disconnectedAt)
        // Player bleibt im State waehrend Grace Period
        assertEquals(2, gameService.gameState.players.size)
    }

    @Test
    fun `handleDisconnect with unknown sessionId is no-op for soft disconnect`() {
        gameService.handleJoin("Alice", "session-1")
        gameService.handleDisconnect("unknown-session")

        assertEquals(1, gameService.gameState.players.size)
        val alice = gameService.gameState.players.first { it.name == "Alice" }
        assertTrue(alice.connected)
    }

    @Test
    fun `disconnect during WAITING_FOR_PLAYERS removes player`() {
        gameService.handleJoin("Alice", "session-1")
        forceDisconnect("session-1")

        assertEquals(0, gameService.gameState.players.size)
        assertEquals(GameStatus.WAITING_FOR_PLAYERS, gameService.gameState.status)
    }

    @Test
    fun `disconnect during IN_PROGRESS sets status to FINISHED`() {
        gameService.handleJoin("Alice", "session-1")
        gameService.handleJoin("Bob", "session-2")

        assertEquals(GameStatus.IN_PROGRESS, gameService.gameState.status)

        forceDisconnect("session-1")

        assertEquals(GameStatus.FINISHED, gameService.gameState.status)
    }

    @Test
    fun `disconnect removes player units`() {
        gameService.handleJoin("Alice", "session-1")
        gameService.handleJoin("Bob", "session-2")

        forceDisconnect("session-1")

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

        forceDisconnect("session-1")

        assertNull(gameService.gameState.currentTurn)
    }

    @Test
    fun `disconnect only removes disconnected player units`() {
        gameService.handleJoin("Alice", "session-1")
        gameService.handleJoin("Bob", "session-2")
        // Combat-Units manuell platzieren (Start-Einheiten werden nicht mehr automatisch gesetzt).
        val s = gameService.gameState
        s.units.add(GameUnit("Alice", 1, 2, UnitType.ARCHER))
        s.units.add(GameUnit("Alice", 2, 3, UnitType.INFANTRY))
        s.units.add(GameUnit("Alice", 3, 2, UnitType.CAVALRY))
        s.units.add(GameUnit("Bob", 8, 7, UnitType.ARCHER))
        s.units.add(GameUnit("Bob", 7, 8, UnitType.INFANTRY))
        s.units.add(GameUnit("Bob", 6, 7, UnitType.CAVALRY))

        forceDisconnect("session-1")

        // 3 regulaere Einheiten (ARCHER, INFANTRY, CAVALRY) + 1 BASE pro Spieler.
        val bobUnits = gameService.gameState.units.filter { it.player == "Bob" }
        assertEquals(4, bobUnits.size)
    }

    @Test
    fun `disconnect when opponent still has units declares opponent as winner`() {
        gameService.handleJoin("Alice", "session-1")
        gameService.handleJoin("Bob", "session-2")

        forceDisconnect("session-1")

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

        forceDisconnect("session-1")

        val state = gameService.gameState
        assertEquals(GameStatus.FINISHED, state.status)
        assertNull(state.winner)
        assertNull(state.currentTurn)
    }

}