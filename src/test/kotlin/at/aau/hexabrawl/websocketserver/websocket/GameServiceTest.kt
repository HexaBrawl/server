package at.aau.hexabrawl.websocketserver.model
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GameServiceTest {

    private lateinit var gameService: GameService

    @BeforeEach
    fun setup() {
        gameService = GameService(CombatService())
    }

    @Test
    fun `test duplicate join and max players`() {
        // 1. Erster Join
        gameService.handleJoin("Alice")
        val stateAfterAlice = gameService.getCurrentState()
        assertThat(stateAfterAlice.players).containsExactly(Player("Alice", gold = GameService.STARTING_GOLD))

        // 2. Doppelter Join (Alice versucht nochmal) -> Darf nichts ändern
        gameService.handleJoin("Alice")
        assertThat(gameService.getCurrentState().players.size).isEqualTo(1)

        // 3. Zweiter Spieler
        gameService.handleJoin("Bob")
        assertThat(gameService.getCurrentState().status).isEqualTo(GameStatus.IN_PROGRESS)

        // 4. Dritter Spieler (Charlie) -> Darf nicht rein (MAX_PLAYERS = 2)
        gameService.handleJoin("Charlie")
        assertThat(gameService.getCurrentState().players).doesNotContain(Player("Charlie"))
    }

    @Test
    fun `test reset functionality`() {
        gameService.handleJoin("Alice")
        gameService.initializeGame()
        val state = gameService.getCurrentState()

        assertThat(state.players).isEmpty()
        assertThat(state.status).isEqualTo(GameStatus.WAITING_FOR_PLAYERS)
    }

    @Test
    fun `test invalid moves`() {

        gameService.initializeGame()   // 🔥 DAS HAT GEFEHLT

        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")

        val aliceBefore = gameService.getCurrentState().units.first {
            it.player == "Alice" && it.type == UnitType.INFANTRY
        }

        val bobBefore = gameService.getCurrentState().units.first {
            it.player == "Bob" && it.type == UnitType.INFANTRY
        }

        // Bob darf NICHT ziehen
        gameService.handleMove(
            Move("Bob", UnitType.INFANTRY,
                bobBefore.x, bobBefore.y,
                bobBefore.x + 1, bobBefore.y + 1)
        )

        val bobAfter = gameService.getCurrentState().units.first {
            it.player == "Bob" && it.type == UnitType.INFANTRY
        }

        assertThat(bobAfter.x).isEqualTo(bobBefore.x)
        assertThat(bobAfter.y).isEqualTo(bobBefore.y)

        // Alice gültiger Move → garantiert freies Feld
        gameService.handleMove(
            Move("Alice", UnitType.INFANTRY,
                aliceBefore.x, aliceBefore.y,
                0, 0) //  garantiert frei
        )

        val aliceAfter = gameService.getCurrentState().units.first {
            it.player == "Alice" && it.type == UnitType.INFANTRY
        }

        assertThat(aliceAfter.x).isEqualTo(0)
        assertThat(aliceAfter.y).isEqualTo(0)

        assertThat(gameService.getCurrentState().currentTurn).isEqualTo("Bob")
    }


    @Test
    fun `test move rejected when game not started`() {
        // Nur Alice ist da, Spiel ist WAITING_FOR_PLAYERS
        gameService.handleJoin("Alice")

        val move = Move(player = "Alice", type = UnitType.INFANTRY, toX = 1, toY = 1)
        gameService.handleMove(move)

        // Status muss immer noch WAITING sein
        assertThat(gameService.getCurrentState().status).isEqualTo(GameStatus.WAITING_FOR_PLAYERS)
    }

    @Test
    fun `test illegal moves and state transitions`() {
        // 1. Initialisierung prüfen (Branch: WAITING_FOR_PLAYERS -> IN_PROGRESS)
        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")
        val stateAfterJoin = gameService.getCurrentState()
        assertThat(stateAfterJoin.players).hasSize(2)

        // 2. Branch: Zug von einem Spieler, der nicht existiert
        val moveUnknown = Move("Charlie", fromX = 0, fromY = 0, toX = 1, toY = 1)
        val stateUnknown = gameService.handleMove(moveUnknown)
        // Erwarte, dass sich nichts geändert hat oder eine Fehlermeldung/Logik greift
        assertThat(stateUnknown.currentTurn).isEqualTo("Alice")

        // 3. Branch: Spieler ist nicht an der Reihe
        val moveWrongTurn = Move( "Bob", type = UnitType.INFANTRY, fromX = 5, fromY = 5, toX = 4, toY = 4)
        val stateWrongTurn = gameService.handleMove(moveWrongTurn)
        // Es sollte immer noch Alice dran sein
        assertThat(stateWrongTurn.currentTurn).isEqualTo("Alice")

        // 4. Branch: Reset bei leerer Spielerliste
        gameService.initializeGame() // Alles auf Null
        val stateAfterInit = gameService.resetToStartCondition()

        // Erwarten hier IN_PROGRESS, da der Soft Reset
        // das Spiel bewusst in diesen Zustand versetzt.
        assertThat(stateAfterInit.status).isEqualTo(GameStatus.IN_PROGRESS)
    }

    @Test
    fun `test reset with existing players`() {
        gameService.handleJoin("Alice")
        // Testet den Branch: if (gameState.players.isNotEmpty()) -> if-Zweig
        val state = gameService.resetToStartCondition()
        assertThat(state.status).isEqualTo(GameStatus.IN_PROGRESS)
        assertThat(state.currentTurn).isEqualTo("Alice")
        assertThat(state.units).isNotEmpty() // Prüft, ob deine neue Unit-Logik greift
    }

    @Test
    fun `test combat removes losing unit and winner advances`() {
        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")

        val state = gameService.getCurrentState()
        val aliceInfantry = state.units.first { it.player == "Alice" && it.type == UnitType.INFANTRY }
        val bobCavalry    = state.units.first { it.player == "Bob"   && it.type == UnitType.CAVALRY  }

        // INFANTRY beats CAVALRY → Alice gewinnt
        gameService.handleMove(Move(
            player = "Alice", type = UnitType.INFANTRY,
            fromX = aliceInfantry.x, fromY = aliceInfantry.y,
            toX = bobCavalry.x, toY = bobCavalry.y
        ))

        val updated = gameService.getCurrentState()
        assertThat(updated.units.none { it.player == "Bob" && it.type == UnitType.CAVALRY }).isTrue()
        val aliceAfter = updated.units.first { it.player == "Alice" && it.type == UnitType.INFANTRY }
        assertThat(aliceAfter.x).isEqualTo(bobCavalry.x)
        assertThat(aliceAfter.y).isEqualTo(bobCavalry.y)
    }

    @Test
    fun `winner is set when last opponent unit dies after move`() {
        gameService.initializeGame()
        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")

        val state = gameService.getCurrentState()
        // Bob behält nur seine CAVALRY (auf der Startposition); ARCHER und INFANTRY entfernen
        state.units.removeIf { it.player == "Bob" && it.type != UnitType.CAVALRY }

        val aliceInfantry = state.units.first { it.player == "Alice" && it.type == UnitType.INFANTRY }
        val bobCavalry    = state.units.first { it.player == "Bob"   && it.type == UnitType.CAVALRY  }

        // INFANTRY beats CAVALRY → Bob hat danach 0 Units, Alice gewinnt
        gameService.handleMove(Move(
            player = "Alice", type = UnitType.INFANTRY,
            fromX = aliceInfantry.x, fromY = aliceInfantry.y,
            toX = bobCavalry.x, toY = bobCavalry.y
        ))

        val updated = gameService.getCurrentState()
        assertThat(updated.status).isEqualTo(GameStatus.FINISHED)
        assertThat(updated.winner).isEqualTo("Alice")
        assertThat(updated.currentTurn).isNull()
    }

    @Test
    fun `match ends as draw when last move kills both combatants`() {
        gameService.initializeGame()
        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")

        val state = gameService.getCurrentState()
        // Beide Spieler haben nur noch je eine INFANTRY → gleicher Typ ⇒ beide sterben im Combat
        state.units.removeIf { it.type != UnitType.INFANTRY }

        val aliceInf = state.units.first { it.player == "Alice" }
        val bobInf   = state.units.first { it.player == "Bob" }

        gameService.handleMove(Move(
            player = "Alice", type = UnitType.INFANTRY,
            fromX = aliceInf.x, fromY = aliceInf.y,
            toX = bobInf.x, toY = bobInf.y
        ))

        val updated = gameService.getCurrentState()
        assertThat(updated.status).isEqualTo(GameStatus.FINISHED)
        assertThat(updated.winner).isNull()
        assertThat(updated.currentTurn).isNull()
    }

    @Test
    fun `match continues when opponent still has other units after combat`() {
        gameService.initializeGame()
        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")

        val state = gameService.getCurrentState()
        val aliceInfantry = state.units.first { it.player == "Alice" && it.type == UnitType.INFANTRY }
        val bobCavalry    = state.units.first { it.player == "Bob"   && it.type == UnitType.CAVALRY  }

        // INFANTRY beats CAVALRY: Bob verliert CAVALRY, hat aber noch ARCHER + INFANTRY
        gameService.handleMove(Move(
            player = "Alice", type = UnitType.INFANTRY,
            fromX = aliceInfantry.x, fromY = aliceInfantry.y,
            toX = bobCavalry.x, toY = bobCavalry.y
        ))

        val updated = gameService.getCurrentState()
        assertThat(updated.status).isEqualTo(GameStatus.IN_PROGRESS)
        assertThat(updated.winner).isNull()
        assertThat(updated.currentTurn).isEqualTo("Bob")
    }

    @Test
    fun `non-combat move does not end the game`() {
        gameService.initializeGame()
        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")

        val state = gameService.getCurrentState()
        val aliceArcher = state.units.first { it.player == "Alice" && it.type == UnitType.ARCHER }

        // Move auf garantiert leeres Feld → kein Combat, kein Unit-Verlust, kein Win
        gameService.handleMove(Move(
            player = "Alice", type = UnitType.ARCHER,
            fromX = aliceArcher.x, fromY = aliceArcher.y,
            toX = 0, toY = 0
        ))

        val updated = gameService.getCurrentState()
        assertThat(updated.status).isEqualTo(GameStatus.IN_PROGRESS)
        assertThat(updated.winner).isNull()
        assertThat(updated.currentTurn).isEqualTo("Bob")
    }

    @Test
    fun `player receives starting gold on join`() {
        gameService.handleJoin("Alice")

        val player = gameService.getCurrentState().players.first { it.name == "Alice" }

        assertThat(player.gold).isEqualTo(GameService.STARTING_GOLD)
    }
}
