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
    fun `match ends as draw when both bases are destroyed simultaneously`() {
        gameService.initializeGame()
        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")

        // Beide Basen kuenstlich entfernen, danach normaler Move ausfuehren -
        // checkWinCondition muss 0 Basen erkennen und Draw setzen.
        val state = gameService.getCurrentState()
        state.units.removeIf { it.type == UnitType.BASE }

        val aliceArcher = state.units.first { it.player == "Alice" && it.type == UnitType.ARCHER }
        gameService.handleMove(Move(
            player = "Alice", type = UnitType.ARCHER,
            fromX = aliceArcher.x, fromY = aliceArcher.y,
            toX = 0, toY = 0
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

    @Test
    fun `player receives starting gold on join`() {
        gameService.handleJoin("Alice")

        val player = gameService.getCurrentState().players.first { it.name == "Alice" }

        assertThat(player.gold).isEqualTo(GameService.STARTING_GOLD)
    }

    @Test
    fun `test applyUpkeep - normaler Abzug`() {
        gameService.initializeGame()
        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")

        val state = gameService.getCurrentState()
        // Beide Spieler bekommen genug Gold
        state.players.forEach { it.gold = 20 }

        // Zug Alice
        val aliceInf = state.units.first { it.player == "Alice" && it.type == UnitType.INFANTRY }
        gameService.handleMove(Move("Alice", UnitType.INFANTRY, aliceInf.x, aliceInf.y, 0, 0))

        // Zug Bob -> Beendet die Runde, applyUpkeep wird getriggert
        val bobInf = state.units.first { it.player == "Bob" && it.type == UnitType.INFANTRY }
        gameService.handleMove(Move("Bob", UnitType.INFANTRY, bobInf.x, bobInf.y, 1, 1))

        // Bei 3 Start-Einheiten kostet der Unterhalt 12 Gold (3+4+5). 20 - 12 = 8.
        assertThat(state.players.first { it.name == "Alice" }.gold).isEqualTo(8)
        assertThat(state.players.first { it.name == "Bob" }.gold).isEqualTo(8)
    }

    @Test
    fun `test applyUpkeep - Grenzfall exakt 0`() {
        gameService.initializeGame()
        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")

        val state = gameService.getCurrentState()
        // Spieler bekommen exakt das Gold für den Unterhalt von 3 Einheiten
        state.players.forEach { it.gold = 12 }

        val aliceInf = state.units.first { it.player == "Alice" && it.type == UnitType.INFANTRY }
        gameService.handleMove(Move("Alice", UnitType.INFANTRY, aliceInf.x, aliceInf.y, 0, 0))

        val bobInf = state.units.first { it.player == "Bob" && it.type == UnitType.INFANTRY }
        gameService.handleMove(Move("Bob", UnitType.INFANTRY, bobInf.x, bobInf.y, 1, 1))

        val alice = state.players.first { it.name == "Alice" }
        // Gold muss genau auf 0 fallen, aber die Einheiten müssen bleiben
        assertThat(alice.gold).isEqualTo(0)
        assertThat(state.units.count { it.player == "Alice" }).isEqualTo(3)
    }

    @Test
    fun `test applyUpkeep - Insolvenz mit Unit-Verlust`() {
        gameService.initializeGame()
        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")

        val state = gameService.getCurrentState()
        // Alice hat 1 Gold zu wenig. Bob hat genug.
        state.players.first { it.name == "Alice" }.gold = 11
        state.players.first { it.name == "Bob" }.gold = 20

        val aliceInf = state.units.first { it.player == "Alice" && it.type == UnitType.INFANTRY }
        gameService.handleMove(Move("Alice", UnitType.INFANTRY, aliceInf.x, aliceInf.y, 0, 0))

        val bobInf = state.units.first { it.player == "Bob" && it.type == UnitType.INFANTRY }
        gameService.handleMove(Move("Bob", UnitType.INFANTRY, bobInf.x, bobInf.y, 1, 1))

        val alice = state.players.first { it.name == "Alice" }

        // Alice geht bankrott: Gold = 0
        assertThat(alice.gold).isEqualTo(0)

        // Die Einheiten sind noch da (3 Stück)
        val aliceUnits = state.units.filter { it.player == "Alice" }
        assertThat(aliceUnits.size).isEqualTo(3)
        // aber sie sind alle zu Skeletten geworden
        assertThat(aliceUnits.all { it.type == UnitType.SKELETON }).isTrue()
    }

    @Test
    fun `test applyUpkeep - Insolvenz loest Win-Condition aus`() {
        gameService.initializeGame()
        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")

        val state = gameService.getCurrentState()
        // Alice hat zu wenig Geld und geht pleite
        state.players.first { it.name == "Alice" }.gold = 5
        state.players.first { it.name == "Bob" }.gold = 20

        val aliceInf = state.units.first { it.player == "Alice" && it.type == UnitType.INFANTRY }
        gameService.handleMove(Move("Alice", UnitType.INFANTRY, aliceInf.x, aliceInf.y, 0, 0))

        val bobInf = state.units.first { it.player == "Bob" && it.type == UnitType.INFANTRY }
        gameService.handleMove(Move("Bob", UnitType.INFANTRY, bobInf.x, bobInf.y, 1, 1))

        // Da Alice durch Insolvenz 0 Einheiten hat, muss Bob sofort gewinnen
        assertThat(state.status).isEqualTo(GameStatus.FINISHED)
        assertThat(state.winner).isEqualTo("Bob")
    }

    @Test
    fun `test buyFarm - erfolgreicher Kauf erhoeht die Kosten fuer die naechste Farm`() {
        gameService.initializeGame()
        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")

        val state = gameService.getCurrentState()
        val alice = state.players.first { it.name == "Alice" }

        alice.gold = 30

        gameService.buyFarm(state, "Alice")
        assertThat(alice.farms).isEqualTo(1)
        assertThat(alice.gold).isEqualTo(18)

        gameService.buyFarm(state, "Alice")
        assertThat(alice.farms).isEqualTo(2)
        assertThat(alice.gold).isEqualTo(4)
    }

    @Test
    fun `test buyFarm - schlaegt bei zu wenig Gold fehl`() {
        gameService.initializeGame()
        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")

        val state = gameService.getCurrentState()
        val alice = state.players.first { it.name == "Alice" }

        alice.gold = 10
        gameService.buyFarm(state, "Alice")

        assertThat(alice.farms).isEqualTo(0)
        assertThat(alice.gold).isEqualTo(10)
    }

    @Test
    fun `test applyUpkeep - Farm-Einkommen verhindert Insolvenz`() {
        gameService.initializeGame()
        gameService.handleJoin("Alice")
        gameService.handleJoin("Bob")
        val state = gameService.getCurrentState()
        val alice = state.players.first { it.name == "Alice" }
        val bob = state.players.first { it.name == "Bob" }

        alice.farms = 1
        alice.gold = 9
        bob.gold = 20

        val aliceInf = state.units.first { it.player == "Alice" && it.type == UnitType.INFANTRY }
        gameService.handleMove(Move("Alice", UnitType.INFANTRY, aliceInf.x, aliceInf.y, 0, 0))
        val bobInf = state.units.first { it.player == "Bob" && it.type == UnitType.INFANTRY }
        gameService.handleMove(Move("Bob", UnitType.INFANTRY, bobInf.x, bobInf.y, 1, 1))

        assertThat(alice.gold).isEqualTo(0)
        assertThat(alice.farms).isEqualTo(1)
        assertThat(state.units.filter { it.player == "Alice" }.all { it.type != UnitType.SKELETON }).isTrue()
    }
}
