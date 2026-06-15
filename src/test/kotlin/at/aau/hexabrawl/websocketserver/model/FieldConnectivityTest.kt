package at.aau.hexabrawl.websocketserver.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import at.aau.hexabrawl.websocketserver.TestServiceFactory
import at.aau.hexabrawl.websocketserver.service.GameService

class FieldConnectivityTest {

    private lateinit var gameService: GameService

    @BeforeEach
    fun setup() {
        gameService = TestServiceFactory.createGameService()
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

    @Test
    fun `own player recaptures own skeleton field via handleMove`() {
        val state = GameState().apply {
            status = GameStatus.IN_PROGRESS
            currentTurn = "Alice"
            players.add(Player(name = "Alice"))
            units.add(GameUnit(player = "Alice", x = 0, y = 0, type = UnitType.BASE))
            units.add(GameUnit(player = "Alice", x = 1, y = 0, type = UnitType.INFANTRY))
            units.add(GameUnit(player = "Alice", x = 0, y = 1, type = UnitType.CAVALRY))
            fields.add(Field(0, 0, owner = "Alice"))
            fields.add(Field(1, 0, owner = "Alice"))
            fields.add(Field(2, 0, owner = "Alice", isSkeleton = true))
        }

        gameService.handleMove(state, Move(
            player = "Alice",
            type = UnitType.INFANTRY,
            fromX = 1, fromY = 0,
            toX = 2, toY = 0
        ))

        val recaptured = state.fields.first { it.x == 2 && it.y == 0 }
        assertEquals("Alice", recaptured.owner)
        assertFalse(recaptured.isSkeleton)
        assertTrue(state.units.any {
            it.player == "Alice" && it.type == UnitType.INFANTRY && it.x == 2 && it.y == 0
        })
    }

    @Test
    fun `opponent captures skeleton field via handleMove`() {
        val state = GameState().apply {
            status = GameStatus.IN_PROGRESS
            currentTurn = "Bob"
            players.add(Player(name = "Alice"))
            players.add(Player(name = "Bob"))
            units.add(GameUnit(player = "Alice", x = 0, y = 0, type = UnitType.BASE))
            units.add(GameUnit(player = "Bob", x = 3, y = 0, type = UnitType.INFANTRY))
            units.add(GameUnit(player = "Bob", x = 4, y = 1, type = UnitType.CAVALRY))
            // Damit Bob nicht eliminiert wird
            // Bobs Basis direkt auf sein Startfeld (3,0) setzen.
            // So bleibt das eroberte Nachbarfeld (2,0) mit der Basis verbunden
            units.add(GameUnit(player = "Bob", x = 3, y = 0, type = UnitType.BASE))
            fields.add(Field(0, 0, owner = "Alice"))
            fields.add(Field(2, 0, owner = "Alice", isSkeleton = true))
            fields.add(Field(3, 0, owner = "Bob"))
        }

        gameService.handleMove(state, Move(
            player = "Bob",
            type = UnitType.INFANTRY,
            fromX = 3, fromY = 0,
            toX = 2, toY = 0
        ))

        val captured = state.fields.first { it.x == 2 && it.y == 0 }
        assertEquals("Bob", captured.owner)
        assertFalse(captured.isSkeleton)
        assertTrue(state.units.any {
            it.player == "Bob" && it.type == UnitType.INFANTRY && it.x == 2 && it.y == 0
        })
    }
}
