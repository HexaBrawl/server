package at.aau.hexabrawl.websocketserver.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import at.aau.hexabrawl.websocketserver.TestServiceFactory
import at.aau.hexabrawl.websocketserver.service.BoardService
import at.aau.hexabrawl.websocketserver.service.PlayerService


class FieldTest {

    private lateinit var playerService: PlayerService
    private lateinit var boardService: BoardService
    private lateinit var gameState: GameState

    @BeforeEach
    fun setup() {
        playerService = TestServiceFactory.createPlayerService()
        boardService = BoardService()
        gameState = GameState()
    }

    @Test
    fun `all board fields are created on game start`() {
        playerService.handleJoin(gameState, "Alice", "s1")
        playerService.handleJoin(gameState, "Bob", "s2")

        // 9x9 = 81 Felder
        assertEquals(81, gameState.fields.size)
    }

    @Test
    fun `Alice has starting territory`() {
        playerService.handleJoin(gameState, "Alice", "s1")
        playerService.handleJoin(gameState, "Bob", "s2")

        val aliceFields = gameState.fields.filter { it.owner == "Alice" }
        assertEquals(7, aliceFields.size)  // Basis + 6 angrenzende Felder
    }

    @Test
    fun `Bob has starting territory`() {
        playerService.handleJoin(gameState, "Alice", "s1")
        playerService.handleJoin(gameState, "Bob", "s2")

        val bobFields = gameState.fields.filter { it.owner == "Bob" }
        assertEquals(7, bobFields.size)
    }

    @Test
    fun `most fields are neutral on game start`() {
        playerService.handleJoin(gameState, "Alice", "s1")
        playerService.handleJoin(gameState, "Bob", "s2")

        val neutralFields = gameState.fields.filter { it.owner == null }
        assertEquals(81 - 14, neutralFields.size)  // 67 neutral
    }

    @Test
    fun `Alice and Bob have non-overlapping territories`() {
        playerService.handleJoin(gameState, "Alice", "s1")
        playerService.handleJoin(gameState, "Bob", "s2")

        val alicePositions = gameState.fields.filter { it.owner == "Alice" }
            .map { it.x to it.y }.toSet()
        val bobPositions = gameState.fields.filter { it.owner == "Bob" }
            .map { it.x to it.y }.toSet()

        assertTrue(alicePositions.intersect(bobPositions).isEmpty())
    }

    @Test
    fun `initializeGame clears all fields`() {
        playerService.handleJoin(gameState, "Alice", "s1")
        playerService.handleJoin(gameState, "Bob", "s2")
        boardService.initializeGame(gameState)

        assertTrue(gameState.fields.isEmpty())
    }

    @Test
    fun `resetToStartCondition re-initializes board`() {
        playerService.handleJoin(gameState, "Alice", "s1")
        playerService.handleJoin(gameState, "Bob", "s2")
        boardService.resetToStartCondition(gameState)

        assertEquals(81, gameState.fields.size)
        assertEquals(7, gameState.fields.count { it.owner == "Alice" })
        assertEquals(7, gameState.fields.count { it.owner == "Bob" })
    }

    @Test
    fun `fields have correct coordinates`() {
        playerService.handleJoin(gameState, "Alice", "s1")
        playerService.handleJoin(gameState, "Bob", "s2")

        // Sanity check: jedes (x,y) im Raster existiert genau einmal
        for (x in 0 until BoardService.DUAL_VALLEY_BOARD_COLS) {
            for (y in 0 until BoardService.DUAL_VALLEY_BOARD_ROWS) {
                val count = gameState.fields.count { it.x == x && it.y == y }
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
