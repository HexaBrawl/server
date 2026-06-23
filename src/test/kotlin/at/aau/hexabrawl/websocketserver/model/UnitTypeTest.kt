package at.aau.hexabrawl.websocketserver.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import at.aau.hexabrawl.websocketserver.TestServiceFactory
import at.aau.hexabrawl.websocketserver.service.BoardService
import at.aau.hexabrawl.websocketserver.service.PlayerService
import at.aau.hexabrawl.websocketserver.service.TurnService


class UnitTypeTest {

    private lateinit var playerService: PlayerService
    private lateinit var turnService: TurnService
    private lateinit var boardService: BoardService
    private lateinit var gameState: GameState

    @BeforeEach
    fun setup() {
        playerService = TestServiceFactory.createPlayerService()
        turnService = TestServiceFactory.createTurnService()
        boardService = BoardService()
        gameState = GameState()

        gameState.players.clear()
        gameState.players.addAll(
            listOf(
                Player("Alice"),
                Player("Bob")
            )
        )

        gameState.currentTurn = "Alice"
        gameState.status = GameStatus.IN_PROGRESS

        gameState.units.clear()
        gameState.units.addAll(listOf(
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
        gameState.fields.clear()
        for (x in 0 until 10) {
            for (y in 0 until 10) {
                gameState.fields.add(Field(x, y))
            }
        }
        listOf(2 to 2, 3 to 2, 4 to 2, 3 to 0).forEach { (x, y) ->
            gameState.fields.first { it.x == x && it.y == y }.owner = "Alice"
        }
        listOf(5 to 5, 6 to 5, 7 to 5, 6 to 7).forEach { (x, y) ->
            gameState.fields.first { it.x == x && it.y == y }.owner = "Bob"
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
        gameState.units.add(GameUnit(player = "Alice", type = UnitType.BASE, x = 3, y = 2))
        gameState.fields.add(Field(3, 2).apply { owner = "Alice" })
        gameState.fields.add(Field(3, 3).apply { owner = "Alice" })

        val move = Move(
            player = "Alice",
            type = UnitType.ARCHER,
            fromX = 2,
            fromY = 2,
            toX = 3,
            toY = 3
        )

        val state = turnService.handleMove(gameState, move).state

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

        val state = turnService.handleMove(gameState, move).state

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

        val state = turnService.handleMove(gameState, move).state

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

        val state = turnService.handleMove(gameState, move).state

        val unit = state.units.find {
            it.player == "Alice" && it.type == UnitType.ARCHER
        }

        assertNotEquals(5, unit?.x)
    }

    @Test
    fun `turn should switch after valid move`() {
        val freshState = GameState()
        playerService.handleJoin(freshState, "Alice", "s1")
        playerService.handleJoin(freshState, "Bob", "s2")
        // Combat-Units manuell platzieren (werden seit Entfernung der
        // Start-Einheiten nicht mehr automatisch gesetzt).
        freshState.units.add(GameUnit("Alice", 1, 2, UnitType.ARCHER))
        freshState.units.add(GameUnit("Alice", 2, 3, UnitType.INFANTRY))
        freshState.units.add(GameUnit("Alice", 3, 2, UnitType.CAVALRY))

        val archer = freshState.units.first {
            it.player == "Alice" && it.type == UnitType.ARCHER
        }

        // Alice bewegt alle drei Einheiten und beendet manuell ihren Zug,
        // dann ist Bob dran.
        turnService.handleMove(freshState, Move(
            player = "Alice", type = UnitType.ARCHER,
            fromX = archer.x, fromY = archer.y,
            toX = archer.x, toY = archer.y + 1
        ))
        turnService.handleMove(freshState, Move("Alice", UnitType.INFANTRY, 2, 3, 2, 4))
        turnService.handleMove(freshState, Move("Alice", UnitType.CAVALRY, 3, 2, 3, 3))
        val state = turnService.endTurn(freshState, "Alice")

        assertEquals("Bob", state.currentTurn)
    }

    @Test
    fun `move fails if unit type does not match`() {
        val freshState = GameState()
        playerService.handleJoin(freshState, "Alice", "s1")
        playerService.handleJoin(freshState, "Bob", "s2")
        // Combat-Units manuell platzieren
        freshState.units.add(GameUnit("Alice", 2, 3, UnitType.INFANTRY))

        val move = Move("Alice", UnitType.ARCHER, 2, 3, 2, 4) // kein ARCHER bei (2,3) - nur INFANTRY

        val state = turnService.handleMove(freshState, move).state

        val unit = state.units.first {
            it.player == "Alice" && it.type == UnitType.INFANTRY
        }

        assertEquals(2, unit.x)
    }


    @Test
    fun `move onto enemy tile triggers combat draw`() {
        val freshState = GameState()
        playerService.handleJoin(freshState, "Alice", "s1")
        playerService.handleJoin(freshState, "Bob", "s2")
        // Combat-Units manuell platzieren
        freshState.units.add(GameUnit("Alice", 3, 2, UnitType.INFANTRY))
        freshState.units.add(GameUnit("Bob", 6, 5, UnitType.INFANTRY))

        val alice = freshState.units.first {
            it.player == "Alice" && it.type == UnitType.INFANTRY
        }
        val bob = freshState.units.first {
            it.player == "Bob" && it.type == UnitType.INFANTRY
        }

        // Bobs INFANTRY in Reichweite von Alice platzieren - die Distanz-Regel
        // (max 2 Hex) wuerde sonst greifen und den Combat-Test verhindern.
        // (4,3) ist frei und Distanz 1 zu Alice INFANTRY auf (3,2).
        bob.x = 4
        bob.y = 3

        val move = Move("Alice", UnitType.INFANTRY, alice.x, alice.y, bob.x, bob.y)
        val state = turnService.handleMove(freshState, move).state

        // INFANTRY vs INFANTRY → Draw → beide weg
        assertNull(state.units.find { it.player == "Alice" && it.type == UnitType.INFANTRY })
        assertNull(state.units.find { it.player == "Bob"  && it.type == UnitType.INFANTRY })
    }


    @Test
    fun `move ignored if game not in progress`() {
        val freshState = GameState()
        // status ist WAITING_FOR_PLAYERS per default

        val move = Move("Alice", UnitType.INFANTRY, 0, 0, 1, 1)

        val state = turnService.handleMove(freshState, move).state

        assertEquals(GameStatus.WAITING_FOR_PLAYERS, state.status)
    }
}
