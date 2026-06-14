package at.aau.hexabrawl.websocketserver.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import at.aau.hexabrawl.websocketserver.TestServiceFactory


class UnitTypeTest {

    private lateinit var gameService: GameService
    private lateinit var gameState: GameState

    @BeforeEach
    fun setup() {
        gameService = TestServiceFactory.createGameService()
        gameState = gameService.gameState

        val state = gameService.gameState

        state.players.clear()
        state.players.addAll(
            listOf(
                Player("Alice"),
                Player("Bob")
            )
        )

        state.currentTurn = "Alice"
        state.status = GameStatus.IN_PROGRESS

        state.units.clear()
        state.units.addAll(listOf(
            GameUnit("Alice", 2, 2, UnitType.ARCHER),
            GameUnit("Alice", 3, 2, UnitType.INFANTRY),
            GameUnit("Alice", 4, 2, UnitType.CAVALRY),

            GameUnit("Bob", 5, 5, UnitType.ARCHER),
            GameUnit("Bob", 6, 5, UnitType.INFANTRY),
            GameUnit("Bob", 7, 5, UnitType.CAVALRY)
        ))

        // Board und Startgebiete initialisieren (analog handleJoin).
        // Wird gebraucht damit die Randfeld-Regel in handleMove greifen
        // kann - ohne Fields wuerde jeder Move als "kein Randfeld"
        // abgelehnt.
        state.fields.clear()
        for (x in 0 until 10) {
            for (y in 0 until 10) {
                state.fields.add(Field(x, y))
            }
        }
        listOf(2 to 2, 3 to 2, 4 to 2, 3 to 0).forEach { (x, y) ->
            state.fields.first { it.x == x && it.y == y }.owner = "Alice"
        }
        listOf(5 to 5, 6 to 5, 7 to 5, 6 to 7).forEach { (x, y) ->
            state.fields.first { it.x == x && it.y == y }.owner = "Bob"
        }
    }

    @Test
    fun `unit type enum contains all expected values`() {
        val types = UnitType.values().toList()

        assertTrue(types.contains(UnitType.ARCHER))
        assertTrue(types.contains(UnitType.INFANTRY))
        assertTrue(types.contains(UnitType.CAVALRY))
    }

    @Test
    fun `move should update correct unit by type`() {
        // Basis direkt neben das Ziel setzen und die Felder Alice geben.
        // Verhindert, dass der Archer auf (3,3) wegen recomputeConnectivity zum Skelett wird.
        gameService.gameState.units.add(GameUnit(player = "Alice", type = UnitType.BASE, x = 3, y = 2))
        gameService.gameState.fields.add(Field(3, 2).apply { owner = "Alice" })
        gameService.gameState.fields.add(Field(3, 3).apply { owner = "Alice" })

        val move = Move(
            player = "Alice",
            type = UnitType.ARCHER,
            fromX = 2,
            fromY = 2,
            toX = 3,
            toY = 3
        )

        val state = gameService.handleMove(move)

        val unit = state.units.find {
            it.player == "Alice" && it.type == UnitType.ARCHER
        }

        assertEquals(3, unit?.x)
        assertEquals(3, unit?.y)
    }

    @Test
    fun `move should fail if unit type does not match`() {
        val move = Move(
            player = "Alice",
            type = UnitType.CAVALRY, // falscher Typ!
            toX = 3,
            toY = 3
        )

        val state = gameService.handleMove(move)

        val unit = state.units.find {
            it.player == "Alice" && it.type == UnitType.CAVALRY
        }

        // Position darf sich NICHT geändert haben
        assertNotEquals(3, unit?.x)
    }

    @Test
    fun `move should be ignored if not current turn`() {
        gameState.currentTurn = "Bob"

        val move = Move(
            player = "Alice",
            type = UnitType.ARCHER,
            toX = 3,
            toY = 3
        )

        val before = gameState.units.toList()

        val state = gameService.handleMove(move)

        assertEquals(before, state.units)
    }

    @Test
    fun `move should fail if target occupied`() {
        val move = Move(
            player = "Alice",
            type = UnitType.ARCHER,
            toX = 5,
            toY = 5 // Bob steht dort
        )

        val state = gameService.handleMove(move)

        val unit = state.units.find {
            it.player == "Alice" && it.type == UnitType.ARCHER
        }

        assertNotEquals(5, unit?.x)
    }

    @Test
    fun `turn should switch after valid move`() {

        gameService.initializeGame()

        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")
        // Combat-Units manuell platzieren (werden seit Entfernung der
        // Start-Einheiten nicht mehr automatisch gesetzt).
        val s = gameService.gameState
        s.units.add(GameUnit("Alice", 1, 2, UnitType.ARCHER))
        s.units.add(GameUnit("Alice", 2, 3, UnitType.INFANTRY))
        s.units.add(GameUnit("Alice", 3, 2, UnitType.CAVALRY))

        val stateBefore = gameService.getCurrentState()

        val archer = stateBefore.units.first {
            it.player == "Alice" && it.type == UnitType.ARCHER
        }

        // Mit Rundensystem switcht der Turn erst wenn alle 3 bewegbaren
        // Einheiten gezogen haben.
        gameService.handleMove(Move(
            player = "Alice", type = UnitType.ARCHER,
            fromX = archer.x, fromY = archer.y,
            toX = archer.x, toY = archer.y + 1
        ))
        gameService.handleMove(Move("Alice", UnitType.INFANTRY, 2, 3, 2, 4))
        val state = gameService.handleMove(Move("Alice", UnitType.CAVALRY, 3, 2, 3, 3))

        assertEquals("Bob", state.currentTurn)
    }

    @Test
    fun `move fails if unit type does not match`() {
        gameService.initializeGame()
        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")
        // Combat-Units manuell platzieren
        gameService.gameState.units.add(GameUnit("Alice", 2, 3, UnitType.INFANTRY))

        val move = Move("Alice", UnitType.ARCHER, 2, 3, 2, 4) // kein ARCHER bei (2,3) - nur INFANTRY

        val state = gameService.handleMove(move)

        val unit = state.units.first {
            it.player == "Alice" && it.type == UnitType.INFANTRY
        }

        assertEquals(2, unit.x)
    }


    @Test
    fun `move onto enemy tile triggers combat draw`() {
        gameService.initializeGame()
        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")
        // Combat-Units manuell platzieren
        val s = gameService.gameState
        s.units.add(GameUnit("Alice", 3, 2, UnitType.INFANTRY))
        s.units.add(GameUnit("Bob", 6, 5, UnitType.INFANTRY))

        val alice = gameService.getCurrentState().units.first {
            it.player == "Alice" && it.type == UnitType.INFANTRY
        }
        val bob = gameService.getCurrentState().units.first {
            it.player == "Bob" && it.type == UnitType.INFANTRY
        }

        // Bobs INFANTRY in Reichweite von Alice platzieren - die Distanz-Regel
        // (max 2 Hex) wuerde sonst greifen und den Combat-Test verhindern.
        // (4,3) ist frei und Distanz 1 zu Alice INFANTRY auf (3,2).
        bob.x = 4
        bob.y = 3

        val move = Move("Alice", UnitType.INFANTRY, alice.x, alice.y, bob.x, bob.y)
        val state = gameService.handleMove(move)

        // INFANTRY vs INFANTRY → Draw → beide weg
        assertNull(state.units.find { it.player == "Alice" && it.type == UnitType.INFANTRY })
        assertNull(state.units.find { it.player == "Bob"  && it.type == UnitType.INFANTRY })
    }


    @Test
    fun `move ignored if game not in progress`() {
        gameService.initializeGame()

        val move = Move("Alice", UnitType.INFANTRY, 0, 0, 1, 1)

        val state = gameService.handleMove(move)

        assertEquals(GameStatus.WAITING_FOR_PLAYERS, state.status)
    }
}