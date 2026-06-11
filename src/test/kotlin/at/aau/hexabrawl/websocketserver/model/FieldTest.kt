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

        // 9x9 = 81 Felder
        assertEquals(81, state.fields.size)
    }

    @Test
    fun `Alice has starting territory`() {
        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")
        val state = gameService.getCurrentState()

        val aliceFields = state.fields.filter { it.owner == "Alice" }
        assertEquals(7, aliceFields.size)  // Basis + 6 angrenzende Felder
    }

    @Test
    fun `Bob has starting territory`() {
        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")
        val state = gameService.getCurrentState()

        val bobFields = state.fields.filter { it.owner == "Bob" }
        assertEquals(7, bobFields.size)
    }

    @Test
    fun `most fields are neutral on game start`() {
        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")
        val state = gameService.getCurrentState()

        val neutralFields = state.fields.filter { it.owner == null }
        assertEquals(81 - 14, neutralFields.size)  // 67 neutral
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
        assertEquals(81, state.fields.size)
        assertEquals(7, state.fields.count { it.owner == "Alice" })
        assertEquals(7, state.fields.count { it.owner == "Bob" })
    }

    @Test
    fun `fields have correct coordinates`() {
        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")
        val state = gameService.getCurrentState()

        // Sanity check: jedes (x,y) im Raster existiert genau einmal
        for (x in 0 until GameService.DUAL_VALLEY_BOARD_COLS) {
            for (y in 0 until GameService.DUAL_VALLEY_BOARD_ROWS) {
                val count = state.fields.count { it.x == x && it.y == y }
                assertEquals(1, count, "Feld ($x,$y) sollte genau einmal existieren")
            }
        }
    }

    @Test
    fun `new field has isSkeleton false by default`() {
        val field = Field(x = 0, y = 0)
        assertFalse(field.isSkeleton)
    }

    @Test
    fun `isSkeleton can be toggled at runtime`() {
        val field = Field(x = 0, y = 0)
        field.isSkeleton = true
        assertTrue(field.isSkeleton)
        field.isSkeleton = false
        assertFalse(field.isSkeleton)
    }
}
