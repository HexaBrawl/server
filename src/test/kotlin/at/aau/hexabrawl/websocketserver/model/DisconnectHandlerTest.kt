package at.aau.hexabrawl.websocketserver.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import at.aau.hexabrawl.websocketserver.TestServiceFactory
import at.aau.hexabrawl.websocketserver.service.PlayerService

class DisconnectHandlerTest {

    private lateinit var playerService: PlayerService
    private lateinit var gameState: GameState

    @BeforeEach
    fun setUp() {
        playerService = TestServiceFactory.createPlayerService()
        gameState = GameState()
    }

    /**
     * Test-Helper: simuliert die kombinierte Soft+Hard-Disconnect-Sequenz
     * (handleDisconnect markiert, hardDelete macht endgueltig). Im echten
     * Code passiert das durch den Scheduled Cleanup nach 30s Grace.
     */
    private fun forceDisconnect(sessionId: String) {
        val player = gameState.players.find { it.sessionId == sessionId } ?: return
        playerService.handleDisconnect(gameState, sessionId)
        playerService.hardDelete(gameState, player)
    }

    @Test
    fun `handleDisconnect alone only marks player as not connected`() {
        playerService.handleJoin(gameState, "Alice", "session-1")
        playerService.handleJoin(gameState, "Bob", "session-2")

        playerService.handleDisconnect(gameState, "session-1")

        val alice = gameState.players.first { it.name == "Alice" }
        assertFalse(alice.connected)
        assertNotNull(alice.disconnectedAt)
        // Player bleibt im State waehrend Grace Period
        assertEquals(2, gameState.players.size)
    }

    @Test
    fun `handleDisconnect with unknown sessionId is no-op for soft disconnect`() {
        playerService.handleJoin(gameState, "Alice", "session-1")
        playerService.handleDisconnect(gameState, "unknown-session")

        assertEquals(1, gameState.players.size)
        val alice = gameState.players.first { it.name == "Alice" }
        assertTrue(alice.connected)
    }

    @Test
    fun `disconnect during WAITING_FOR_PLAYERS removes player`() {
        playerService.handleJoin(gameState, "Alice", "session-1")
        forceDisconnect("session-1")

        assertEquals(0, gameState.players.size)
        assertEquals(GameStatus.WAITING_FOR_PLAYERS, gameState.status)
    }

    @Test
    fun `disconnect during IN_PROGRESS sets status to FINISHED`() {
        playerService.handleJoin(gameState, "Alice", "session-1")
        playerService.handleJoin(gameState, "Bob", "session-2")

        assertEquals(GameStatus.IN_PROGRESS, gameState.status)

        forceDisconnect("session-1")

        assertEquals(GameStatus.FINISHED, gameState.status)
    }

    @Test
    fun `disconnect removes player units`() {
        playerService.handleJoin(gameState, "Alice", "session-1")
        playerService.handleJoin(gameState, "Bob", "session-2")

        forceDisconnect("session-1")

        val aliceUnits = gameState.units.filter { it.player == "Alice" }
        assertEquals(0, aliceUnits.size)
    }

    @Test
    fun `disconnect with unknown sessionId does nothing`() {
        playerService.handleJoin(gameState, "Alice", "session-1")
        playerService.handleDisconnect(gameState, "unknown-session")

        assertEquals(1, gameState.players.size)
    }

    @Test
    fun `disconnect sets currentTurn to null when IN_PROGRESS`() {
        playerService.handleJoin(gameState, "Alice", "session-1")
        playerService.handleJoin(gameState, "Bob", "session-2")

        forceDisconnect("session-1")

        assertNull(gameState.currentTurn)
    }

    @Test
    fun `disconnect only removes disconnected player units`() {
        playerService.handleJoin(gameState, "Alice", "session-1")
        playerService.handleJoin(gameState, "Bob", "session-2")
        // Combat-Units manuell platzieren (Start-Einheiten werden nicht mehr automatisch gesetzt).
        gameState.units.add(GameUnit("Alice", 1, 2, UnitType.ARCHER))
        gameState.units.add(GameUnit("Alice", 2, 3, UnitType.INFANTRY))
        gameState.units.add(GameUnit("Alice", 3, 2, UnitType.CAVALRY))
        gameState.units.add(GameUnit("Bob", 8, 7, UnitType.ARCHER))
        gameState.units.add(GameUnit("Bob", 7, 8, UnitType.INFANTRY))
        gameState.units.add(GameUnit("Bob", 6, 7, UnitType.CAVALRY))

        forceDisconnect("session-1")

        // 3 regulaere Einheiten (ARCHER, INFANTRY, CAVALRY) + 1 BASE pro Spieler.
        val bobUnits = gameState.units.filter { it.player == "Bob" }
        assertEquals(4, bobUnits.size)
    }

    @Test
    fun `disconnect when opponent still has units declares opponent as winner`() {
        playerService.handleJoin(gameState, "Alice", "session-1")
        playerService.handleJoin(gameState, "Bob", "session-2")

        forceDisconnect("session-1")

        assertEquals(GameStatus.FINISHED, gameState.status)
        assertEquals("Bob", gameState.winner)
        assertNull(gameState.currentTurn)
    }

    @Test
    fun `disconnect when no player has units left ends as draw`() {
        playerService.handleJoin(gameState, "Alice", "session-1")
        playerService.handleJoin(gameState, "Bob", "session-2")

        // Bobs Units entfernen, damit nach Alice's Disconnect niemand mehr
        // Einheiten auf dem Brett hat -> Unentschieden
        gameState.units.removeIf { it.player == "Bob" }

        forceDisconnect("session-1")

        assertEquals(GameStatus.FINISHED, gameState.status)
        assertNull(gameState.winner)
        assertNull(gameState.currentTurn)
    }

}
