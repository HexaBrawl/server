package at.aau.hexabrawl.websocketserver.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FieldConnectivityTest {

    private lateinit var gameService: GameService

    @BeforeEach
    fun setup() {
        gameService = GameService(CombatService())
    }

    private fun stateWithBase(owner: String, baseX: Int, baseY: Int): GameState =
        GameState().apply {
            players.add(Player(name = owner))
            units.add(GameUnit(player = owner, x = baseX, y = baseY, type = UnitType.BASE))
            fields.add(Field(baseX, baseY, owner = owner))
        }

    @Test
    fun `connected territory stays connected`() {
        val state = stateWithBase("Alice", 0, 0).apply {
            fields.add(Field(1, 0, owner = "Alice"))
            fields.add(Field(2, 0, owner = "Alice"))
        }
        gameService.recomputeConnectivity(state)
        assertTrue(state.fields.none { it.isSkeleton })
    }

    @Test
    fun `isolated field becomes skeleton`() {
        val state = stateWithBase("Alice", 0, 0).apply {
            // (1,0) fehlt – (2,0) hat keinen Pfad zur BASE
            fields.add(Field(2, 0, owner = "Alice"))
        }
        gameService.recomputeConnectivity(state)
        val baseField = state.fields.first { it.x == 0 && it.y == 0 }
        val isolated = state.fields.first { it.x == 2 && it.y == 0 }
        assertFalse(baseField.isSkeleton)
        assertTrue(isolated.isSkeleton)
    }

    @Test
    fun `unit on isolated field becomes skeleton unit`() {
        val state = stateWithBase("Alice", 0, 0).apply {
            fields.add(Field(2, 0, owner = "Alice"))
            units.add(GameUnit(player = "Alice", x = 2, y = 0, type = UnitType.INFANTRY))
        }
        gameService.recomputeConnectivity(state)
        val infantry = state.units.first { it.x == 2 && it.y == 0 }
        assertEquals(UnitType.SKELETON, infantry.type)
    }

    @Test
    fun `narrow corridor keeps territory connected`() {
        val state = stateWithBase("Alice", 0, 0).apply {
            // Hex-Kette BASE (0,0) - (1,0) - (2,0) - (3,0) - (4,0)
            fields.add(Field(1, 0, owner = "Alice"))
            fields.add(Field(2, 0, owner = "Alice"))
            fields.add(Field(3, 0, owner = "Alice"))
            fields.add(Field(4, 0, owner = "Alice"))
        }
        gameService.recomputeConnectivity(state)
        assertTrue(state.fields.none { it.isSkeleton })
    }

    @Test
    fun `player without base is skipped without error`() {
        val state = GameState().apply {
            players.add(Player(name = "Alice"))
            // Keine BASE-Unit – Spieler ist via checkWinCondition ohnehin raus
            fields.add(Field(0, 0, owner = "Alice"))
            fields.add(Field(1, 0, owner = "Alice"))
        }
        gameService.recomputeConnectivity(state)
        assertTrue(state.fields.none { it.isSkeleton })
    }

    @Test
    fun `skeleton corridor blocks bfs path`() {
        val state = stateWithBase("Alice", 0, 0).apply {
            // (1,0) ist Alice aber bereits SKELETON – sollte als Pfad nicht zählen
            fields.add(Field(1, 0, owner = "Alice", isSkeleton = true))
            fields.add(Field(2, 0, owner = "Alice"))
        }
        gameService.recomputeConnectivity(state)
        val field20 = state.fields.first { it.x == 2 && it.y == 0 }
        assertTrue(field20.isSkeleton)
    }
}
