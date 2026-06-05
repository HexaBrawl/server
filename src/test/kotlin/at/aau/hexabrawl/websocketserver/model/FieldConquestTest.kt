package at.aau.hexabrawl.websocketserver.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FieldConquestTest {

    private lateinit var gameService: GameService

    @BeforeEach
    fun setup() {
        gameService = GameService(CombatService())
        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")
    }

    @Test
    fun `moving onto neutral border field conquers it`() {
        // Alice INFANTRY (3,2) -> (3,3): (3,3) ist neutrales Randfeld.
        gameService.handleMove(Move("Alice", UnitType.INFANTRY, 3, 2, 3, 3))

        val field = gameService.getCurrentState().fields.first { it.x == 3 && it.y == 3 }
        assertEquals("Alice", field.owner)
    }

    @Test
    fun `moving to a non-border field is rejected`() {
        // (3,4) ist nicht Alice's Feld und nicht adjacent zu Alice's Gebiet.
        val state = gameService.handleMove(Move("Alice", UnitType.INFANTRY, 3, 2, 3, 4))

        // Position unveraendert
        val infantry = state.units.first { it.player == "Alice" && it.type == UnitType.INFANTRY }
        assertEquals(3, infantry.x)
        assertEquals(2, infantry.y)
    }

    @Test
    fun `own territory fields stay owned after move from them`() {
        // Alice INFANTRY zieht von (3,2) auf (3,3) - (3,2) gehoert weiterhin Alice
        gameService.handleMove(Move("Alice", UnitType.INFANTRY, 3, 2, 3, 3))

        val field = gameService.getCurrentState().fields.first { it.x == 3 && it.y == 2 }
        assertEquals("Alice", field.owner)
    }

    @Test
    fun `skeleton is removed when unit moves onto its tile`() {
        val state = gameService.getCurrentState()
        // Skelett manuell auf (3,3) platzieren (Randfeld von Alice).
        state.units.add(GameUnit("Bob", 3, 3, UnitType.SKELETON))

        gameService.handleMove(Move("Alice", UnitType.INFANTRY, 3, 2, 3, 3))

        val skeletonsLeft = state.units.filter { it.type == UnitType.SKELETON }
        assertTrue(skeletonsLeft.isEmpty(), "Skelett haette entfernt werden muessen")
    }

    @Test
    fun `conquered field becomes part of player territory`() {
        gameService.handleMove(Move("Alice", UnitType.INFANTRY, 3, 2, 3, 3))

        val aliceFields = gameService.getCurrentState().fields.count { it.owner == "Alice" }
        // 4 Startfelder + 1 eroberte = 5
        assertEquals(5, aliceFields)
    }

    @Test
    fun `enemy field adjacent to own territory can be conquered`() {
        val state = gameService.getCurrentState()
        // Bob's Feld manuell auf (3,3) setzen (= adjacent zu Alice's (3,2)).
        state.fields.first { it.x == 3 && it.y == 3 }.owner = "Bob"

        // Alice INFANTRY zieht hin und erobert.
        gameService.handleMove(Move("Alice", UnitType.INFANTRY, 3, 2, 3, 3))

        val updated = state.fields.first { it.x == 3 && it.y == 3 }
        assertEquals("Alice", updated.owner)
    }
}
