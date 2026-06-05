package at.aau.hexabrawl.websocketserver.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FieldTest {

    private lateinit var gameService: GameService

    @BeforeEach
    fun setup() {
        gameService = GameService(CombatService())
    }

    @Test
    fun `all board fields are created on game start`() {
        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")
        val state = gameService.getCurrentState()

        // 10x10 = 100 Felder
        assertEquals(100, state.fields.size)
    }

    @Test
    fun `Alice has starting territory`() {
        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")
        val state = gameService.getCurrentState()

        val aliceFields = state.fields.filter { it.owner == "Alice" }
        assertEquals(4, aliceFields.size)  // 3 Einheiten + 1 Basis
    }

    @Test
    fun `Bob has starting territory`() {
        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")
        val state = gameService.getCurrentState()

        val bobFields = state.fields.filter { it.owner == "Bob" }
        assertEquals(4, bobFields.size)
    }

    @Test
    fun `most fields are neutral on game start`() {
        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")
        val state = gameService.getCurrentState()

        val neutralFields = state.fields.filter { it.owner == null }
        assertEquals(100 - 8, neutralFields.size)  // 92 neutral
    }

    @Test
    fun `Alice and Bob have non-overlapping territories`() {
        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")
        val state = gameService.getCurrentState()

        val alicePositions = state.fields.filter { it.owner == "Alice" }
            .map { it.x to it.y }.toSet()
        val bobPositions = state.fields.filter { it.owner == "Bob" }
            .map { it.x to it.y }.toSet()

        assertTrue(alicePositions.intersect(bobPositions).isEmpty())
    }

    @Test
    fun `initializeGame clears all fields`() {
        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")
        gameService.initializeGame()

        assertTrue(gameService.getCurrentState().fields.isEmpty())
    }

    @Test
    fun `resetToStartCondition re-initializes board`() {
        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")
        gameService.resetToStartCondition()

        val state = gameService.getCurrentState()
        assertEquals(100, state.fields.size)
        assertEquals(4, state.fields.count { it.owner == "Alice" })
        assertEquals(4, state.fields.count { it.owner == "Bob" })
    }

    @Test
    fun `fields have correct coordinates`() {
        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")
        val state = gameService.getCurrentState()

        // Sanity check: jedes (x,y) im Raster existiert genau einmal
        for (x in 0 until GameService.BOARD_COLS) {
            for (y in 0 until GameService.BOARD_ROWS) {
                val count = state.fields.count { it.x == x && it.y == y }
                assertEquals(1, count, "Feld ($x,$y) sollte genau einmal existieren")
            }
        }
    }
}
