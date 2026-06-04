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
        assertThat(stateAfterAlice.players).containsExactly(Player("Alice"))

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

        // Alice gültiger Move → nahes freies Feld (Distanz 1).
        // Vorher wurde (0,0) verwendet, das ist mit der 2-Hex-Regel zu weit.
        val aliceTargetX = aliceBefore.x
        val aliceTargetY = aliceBefore.y + 1
        gameService.handleMove(
            Move("Alice", UnitType.INFANTRY,
                aliceBefore.x, aliceBefore.y,
                aliceTargetX, aliceTargetY)
        )

        val aliceAfter = gameService.getCurrentState().units.first {
            it.player == "Alice" && it.type == UnitType.INFANTRY
        }

        assertThat(aliceAfter.x).isEqualTo(aliceTargetX)
        assertThat(aliceAfter.y).isEqualTo(aliceTargetY)

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

        // Bobs CAVALRY in Reichweite platzieren (Distanz 1, freies Feld).
        bobCavalry.x = aliceInfantry.x
        bobCavalry.y = aliceInfantry.y + 1

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
    fun `match ends as draw when both bases are destroyed simultaneously`() {
        gameService.initializeGame()
        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")

        // Beide Basen kuenstlich entfernen, danach normaler Move ausfuehren -
        // checkWinCondition muss 0 Basen erkennen und Draw setzen.
        val state = gameService.getCurrentState()
        state.units.removeIf { it.type == UnitType.BASE }

        val aliceArcher = state.units.first { it.player == "Alice" && it.type == UnitType.ARCHER }
        // Move auf nahes leeres Feld (Distanz 1) - mit der 2-Hex-Regel ist (0,0) zu weit.
        gameService.handleMove(Move(
            player = "Alice", type = UnitType.ARCHER,
            fromX = aliceArcher.x, fromY = aliceArcher.y,
            toX = aliceArcher.x, toY = aliceArcher.y + 1
        ))

        val updated = gameService.getCurrentState()
        assertThat(updated.status).isEqualTo(GameStatus.FINISHED)
        assertThat(updated.winner).isNull()
        assertThat(updated.currentTurn).isNull()
    }

    @Test
    fun `match continues when both bases still stand after combat`() {
        gameService.initializeGame()
        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")

        val state = gameService.getCurrentState()
        val aliceInfantry = state.units.first { it.player == "Alice" && it.type == UnitType.INFANTRY }
        val bobCavalry    = state.units.first { it.player == "Bob"   && it.type == UnitType.CAVALRY  }

        // Bobs CAVALRY in Reichweite platzieren (Distanz 1, freies Feld).
        bobCavalry.x = aliceInfantry.x
        bobCavalry.y = aliceInfantry.y + 1

        // INFANTRY beats CAVALRY: Bob verliert CAVALRY, beide Basen stehen weiterhin
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

        // Move auf nahes leeres Feld - kein Combat, kein Unit-Verlust, kein Win.
        // (0,0) waere ausserhalb der 2-Hex-Reichweite.
        gameService.handleMove(Move(
            player = "Alice", type = UnitType.ARCHER,
            fromX = aliceArcher.x, fromY = aliceArcher.y,
            toX = aliceArcher.x, toY = aliceArcher.y + 1
        ))

        val updated = gameService.getCurrentState()
        assertThat(updated.status).isEqualTo(GameStatus.IN_PROGRESS)
        assertThat(updated.winner).isNull()
        assertThat(updated.currentTurn).isEqualTo("Bob")
    }

    @Test
    fun `base unit is spawned for each player at configured position when game starts`() {
        gameService.initializeGame()
        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")

        val state = gameService.getCurrentState()
        val bases = state.units.filter { it.type == UnitType.BASE }

        assertThat(bases).hasSize(2)

        val aliceBase = bases.first { it.player == "Alice" }
        val bobBase   = bases.first { it.player == "Bob" }

        assertThat(aliceBase.x).isEqualTo(GameService.BASE_POSITION_P1.first)
        assertThat(aliceBase.y).isEqualTo(GameService.BASE_POSITION_P1.second)
        assertThat(bobBase.x).isEqualTo(GameService.BASE_POSITION_P2.first)
        assertThat(bobBase.y).isEqualTo(GameService.BASE_POSITION_P2.second)
    }

    @Test
    fun `move attempt with BASE type is rejected and state unchanged`() {
        gameService.initializeGame()
        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")

        val state = gameService.getCurrentState()
        val aliceBase = state.units.first { it.player == "Alice" && it.type == UnitType.BASE }
        val turnBefore = state.currentTurn

        // Versuch, die eigene Basis zu bewegen - muss ignoriert werden
        gameService.handleMove(Move(
            player = "Alice",
            type = UnitType.BASE,
            fromX = aliceBase.x,
            fromY = aliceBase.y,
            toX = aliceBase.x + 1,
            toY = aliceBase.y
        ))

        val updated = gameService.getCurrentState()
        val aliceBaseAfter = updated.units.first { it.player == "Alice" && it.type == UnitType.BASE }

        // Basis steht weiterhin auf ihrer Startposition
        assertThat(aliceBaseAfter.x).isEqualTo(aliceBase.x)
        assertThat(aliceBaseAfter.y).isEqualTo(aliceBase.y)
        // currentTurn hat sich nicht geaendert - der Move wurde komplett ignoriert
        assertThat(updated.currentTurn).isEqualTo(turnBefore)
    }

    @Test
    fun `regular units are still spawned alongside BASE`() {
        // Sanity check: Einfuehrung der BASE darf die regulaeren Start-Units nicht stoeren
        gameService.initializeGame()
        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")

        val state = gameService.getCurrentState()
        val regularTypes = listOf(UnitType.ARCHER, UnitType.INFANTRY, UnitType.CAVALRY)

        regularTypes.forEach { type ->
            val aliceUnit = state.units.firstOrNull { it.player == "Alice" && it.type == type }
            val bobUnit   = state.units.firstOrNull { it.player == "Bob"   && it.type == type }
            assertThat(aliceUnit).withFailMessage("Alice should still have a $type").isNotNull
            assertThat(bobUnit).withFailMessage("Bob should still have a $type").isNotNull
        }
    }

    @Test
    fun `winner is set when attacker reaches opponent base`() {
        gameService.initializeGame()
        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")

        val state = gameService.getCurrentState()
        val aliceInfantry = state.units.first { it.player == "Alice" && it.type == UnitType.INFANTRY }
        val bobBase       = state.units.first { it.player == "Bob"   && it.type == UnitType.BASE }

        // Bobs BASE in Reichweite verschieben (Distanz 1) - mit der 2-Hex-Regel
        // ist die Originalposition (6,7) sonst nicht in einem Zug erreichbar.
        bobBase.x = aliceInfantry.x
        bobBase.y = aliceInfantry.y + 1

        // Alice zieht ihre INFANTRY direkt auf Bob's Basis-Hex - das beendet
        // das Spiel sofort, unabhaengig vom Stein-Schere-Papier-System.
        gameService.handleMove(Move(
            player = "Alice", type = UnitType.INFANTRY,
            fromX = aliceInfantry.x, fromY = aliceInfantry.y,
            toX = bobBase.x, toY = bobBase.y
        ))

        val updated = gameService.getCurrentState()
        assertThat(updated.status).isEqualTo(GameStatus.FINISHED)
        assertThat(updated.winner).isEqualTo("Alice")
        assertThat(updated.currentTurn).isNull()

        // Bob's Basis ist entfernt
        assertThat(updated.units.any { it.player == "Bob" && it.type == UnitType.BASE }).isFalse()

        // Alice's INFANTRY steht jetzt auf der ehemaligen Basis-Position
        val aliceAfter = updated.units.first { it.player == "Alice" && it.type == UnitType.INFANTRY }
        assertThat(aliceAfter.x).isEqualTo(bobBase.x)
        assertThat(aliceAfter.y).isEqualTo(bobBase.y)
    }

    @Test
    fun `regular unit killed by combat does NOT end the match`() {
        // Stellt sicher, dass die alte "alle Units tot = Sieg"-Regel nicht
        // mehr greift. Nur Basis-Zerstoerung darf das Spiel beenden.
        gameService.initializeGame()
        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")

        val state = gameService.getCurrentState()

        // Reduziere Alice's regulaere Units auf nur die INFANTRY,
        // ihre Basis bleibt aber stehen.
        state.units.removeIf {
            it.player == "Alice" &&
                it.type != UnitType.INFANTRY &&
                it.type != UnitType.BASE
        }
        // Auch Bob's Units reduzieren - er greift mit CAVALRY an
        state.units.removeIf {
            it.player == "Bob" &&
                it.type != UnitType.CAVALRY &&
                it.type != UnitType.BASE
        }

        val aliceInfantry = state.units.first { it.player == "Alice" && it.type == UnitType.INFANTRY }
        val bobCavalry    = state.units.first { it.player == "Bob"   && it.type == UnitType.CAVALRY }

        // Bobs CAVALRY in Reichweite platzieren (Distanz 1, freies Feld).
        bobCavalry.x = aliceInfantry.x
        bobCavalry.y = aliceInfantry.y + 1

        // Alice greift Bob's CAVALRY an. INFANTRY beats CAVALRY,
        // Bob verliert seine einzige regulaere Unit - hat aber noch Basis.
        gameService.handleMove(Move(
            player = "Alice", type = UnitType.INFANTRY,
            fromX = aliceInfantry.x, fromY = aliceInfantry.y,
            toX = bobCavalry.x, toY = bobCavalry.y
        ))

        val updated = gameService.getCurrentState()
        assertThat(updated.status).isEqualTo(GameStatus.IN_PROGRESS)
        assertThat(updated.winner).isNull()

        // Bob hat keine regulaeren Units mehr, aber seine Basis steht noch
        val bobRegularUnits = updated.units.filter {
            it.player == "Bob" && it.type != UnitType.BASE
        }
        assertThat(bobRegularUnits).isEmpty()
        assertThat(updated.units.any { it.player == "Bob" && it.type == UnitType.BASE }).isTrue
    }

}
