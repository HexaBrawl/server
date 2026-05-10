package at.aau.hexabrawl.websocketserver.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DisconnectHandlerTest {

    private lateinit var gameService: GameService

    @BeforeEach
    fun setUp() {
        gameService = GameService()
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

        val bobUnits = gameService.gameState.units.filter { it.player == "Bob" }
        assertEquals(3, bobUnits.size)
    }

}