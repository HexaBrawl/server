package at.aau.hexabrawl.websocketserver.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import at.aau.hexabrawl.websocketserver.TestServiceFactory
import at.aau.hexabrawl.websocketserver.service.PlayerService
import at.aau.hexabrawl.websocketserver.service.TurnService


class FieldConquestTest {

    private lateinit var playerService: PlayerService
    private lateinit var turnService: TurnService
    private lateinit var gameState: GameState

    @BeforeEach
    fun setup() {
        playerService = TestServiceFactory.createPlayerService()
        turnService = TestServiceFactory.createTurnService()
        gameState = GameState()
        playerService.handleJoin(gameState, "Alice", "session-alice")
        playerService.handleJoin(gameState, "Bob", "session-bob")
        // Standard-Combat-Units platzieren (seit Entfernen der Start-Einheiten
        // werden die nicht mehr automatisch vom Server gesetzt).
        gameState.units.add(GameUnit("Alice", 1, 2, UnitType.ARCHER))
        gameState.units.add(GameUnit("Alice", 2, 3, UnitType.INFANTRY))
        gameState.units.add(GameUnit("Alice", 3, 2, UnitType.CAVALRY))
        gameState.units.add(GameUnit("Bob", 8, 7, UnitType.ARCHER))
        gameState.units.add(GameUnit("Bob", 7, 8, UnitType.INFANTRY))
        gameState.units.add(GameUnit("Bob", 6, 7, UnitType.CAVALRY))
    }

    @Test
    fun `moving onto neutral border field conquers it`() {
        // Alice INFANTRY (2,3) -> (2,4): (2,4) ist neutrales Randfeld.
        turnService.handleMove(gameState, Move("Alice", UnitType.INFANTRY, 2, 3, 2, 4))

        val field = gameState.fields.first { it.x == 2 && it.y == 4 }
        assertEquals("Alice", field.owner)
    }

    @Test
    fun `moving to a non-border field is rejected`() {
        // (0,4) liegt nicht angrenzend an Alice's Gebiet.
        val state = turnService.handleMove(gameState, Move("Alice", UnitType.INFANTRY, 2, 3, 0, 4)).state

        // Position unveraendert
        val infantry = state.units.first { it.player == "Alice" && it.type == UnitType.INFANTRY }
        assertEquals(2, infantry.x)
        assertEquals(3, infantry.y)
    }

    @Test
    fun `own territory fields stay owned after move from them`() {
        // Alice INFANTRY zieht von (2,3) auf (2,4) - (2,3) gehoert weiterhin Alice
        turnService.handleMove(gameState, Move("Alice", UnitType.INFANTRY, 2, 3, 2, 4))

        val field = gameState.fields.first { it.x == 2 && it.y == 3 }
        assertEquals("Alice", field.owner)
    }

    @Test
    fun `skeleton is removed when unit moves onto its tile`() {
        // Skelett manuell auf (2,4) platzieren (Randfeld von Alice).
        gameState.units.add(GameUnit("Bob", 2, 4, UnitType.SKELETON))

        turnService.handleMove(gameState, Move("Alice", UnitType.INFANTRY, 2, 3, 2, 4))

        val skeletonsLeft = gameState.units.filter { it.type == UnitType.SKELETON }
        assertTrue(skeletonsLeft.isEmpty(), "Skelett haette entfernt werden muessen")
    }

    @Test
    fun `conquered field becomes part of player territory`() {
        turnService.handleMove(gameState, Move("Alice", UnitType.INFANTRY, 2, 3, 2, 4))

        val aliceFields = gameState.fields.count { it.owner == "Alice" }
        // 7 Startfelder + 1 erobertes = 8
        assertEquals(8, aliceFields)
    }

    @Test
    fun `enemy field adjacent to own territory can be conquered`() {
        // Bob's Feld manuell auf (2,4) setzen (= adjacent zu Alice's (2,3)).
        gameState.fields.first { it.x == 2 && it.y == 4 }.owner = "Bob"

        // Alice INFANTRY zieht hin und erobert.
        turnService.handleMove(gameState, Move("Alice", UnitType.INFANTRY, 2, 3, 2, 4))

        val updated = gameState.fields.first { it.x == 2 && it.y == 4 }
        assertEquals("Alice", updated.owner)
    }
}
