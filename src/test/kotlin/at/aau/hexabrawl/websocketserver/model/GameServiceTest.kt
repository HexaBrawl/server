package at.aau.hexabrawl.websocketserver.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import at.aau.hexabrawl.websocketserver.TestServiceFactory
import at.aau.hexabrawl.websocketserver.service.BoardService
import at.aau.hexabrawl.websocketserver.service.CheatGiftService
import at.aau.hexabrawl.websocketserver.service.EconomyService
import at.aau.hexabrawl.websocketserver.service.PlayerService
import at.aau.hexabrawl.websocketserver.service.TurnService

class GameServiceTest {

    private lateinit var playerService: PlayerService
    private lateinit var turnService: TurnService
    private lateinit var economyService: EconomyService
    private lateinit var cheatGiftService: CheatGiftService
    private lateinit var boardService: BoardService
    private lateinit var gameState: GameState

    @BeforeEach
    fun setup() {
        boardService = BoardService()
        playerService = PlayerService(boardService)
        economyService = EconomyService()
        cheatGiftService = CheatGiftService()
        turnService = TestServiceFactory.createTurnService()
        gameState = GameState()
    }

    private fun seedDualValleyCombatUnits() {
        gameState.units.add(GameUnit("Alice", 1, 2, UnitType.ARCHER))
        gameState.units.add(GameUnit("Alice", 2, 3, UnitType.INFANTRY))
        gameState.units.add(GameUnit("Alice", 3, 2, UnitType.CAVALRY))
        gameState.units.add(GameUnit("Bob", 8, 7, UnitType.ARCHER))
        gameState.units.add(GameUnit("Bob", 7, 8, UnitType.INFANTRY))
        gameState.units.add(GameUnit("Bob", 6, 7, UnitType.CAVALRY))
    }

    @Test
    fun `test duplicate join and max players`() {
        playerService.handleJoin(gameState, "Alice")
        assertThat(gameState.players).containsExactly(Player("Alice", gold = EconomyService.STARTING_GOLD))

        playerService.handleJoin(gameState, "Alice")
        assertThat(gameState.players.size).isEqualTo(1)

        playerService.handleJoin(gameState, "Bob")
        seedDualValleyCombatUnits()
        assertThat(gameState.status).isEqualTo(GameStatus.IN_PROGRESS)

        playerService.handleJoin(gameState, "Charlie")
        assertThat(gameState.players).doesNotContain(Player("Charlie"))
    }

    @Test
    fun `test reset functionality`() {
        playerService.handleJoin(gameState, "Alice")
        boardService.initializeGame(gameState)

        assertThat(gameState.players).isEmpty()
        assertThat(gameState.status).isEqualTo(GameStatus.WAITING_FOR_PLAYERS)
    }

    @Test
    fun `test invalid moves`() {
        playerService.handleJoin(gameState, "Alice")
        playerService.handleJoin(gameState, "Bob")
        seedDualValleyCombatUnits()

        val aliceBefore = gameState.units.first { it.player == "Alice" && it.type == UnitType.INFANTRY }
        val bobBefore = gameState.units.first { it.player == "Bob" && it.type == UnitType.INFANTRY }

        turnService.handleMove(gameState,
            Move("Bob", UnitType.INFANTRY, bobBefore.x, bobBefore.y, bobBefore.x + 1, bobBefore.y + 1))

        val bobAfter = gameState.units.first { it.player == "Bob" && it.type == UnitType.INFANTRY }
        assertThat(bobAfter.x).isEqualTo(bobBefore.x)
        assertThat(bobAfter.y).isEqualTo(bobBefore.y)

        val aliceTargetX = aliceBefore.x
        val aliceTargetY = aliceBefore.y + 1
        turnService.handleMove(gameState, Move("Alice", UnitType.ARCHER, 1, 2, 1, 3))
        turnService.handleMove(gameState, Move("Alice", UnitType.INFANTRY,
            aliceBefore.x, aliceBefore.y, aliceTargetX, aliceTargetY))
        turnService.handleMove(gameState, Move("Alice", UnitType.CAVALRY, 3, 2, 3, 3))
        turnService.endTurn(gameState, "Alice")

        val aliceAfter = gameState.units.first { it.player == "Alice" && it.type == UnitType.INFANTRY }
        assertThat(aliceAfter.x).isEqualTo(aliceTargetX)
        assertThat(aliceAfter.y).isEqualTo(aliceTargetY)
        assertThat(gameState.currentTurn).isEqualTo("Bob")
    }

    @Test
    fun `test move rejected when game not started`() {
        playerService.handleJoin(gameState, "Alice")

        val move = Move(player = "Alice", type = UnitType.INFANTRY, toX = 1, toY = 1)
        turnService.handleMove(gameState, move)

        assertThat(gameState.status).isEqualTo(GameStatus.WAITING_FOR_PLAYERS)
    }

    @Test
    fun `test illegal moves and state transitions`() {
        playerService.handleJoin(gameState, "Alice")
        playerService.handleJoin(gameState, "Bob")
        seedDualValleyCombatUnits()
        assertThat(gameState.players).hasSize(2)

        val stateUnknown = turnService.handleMove(gameState,
            Move("Charlie", fromX = 0, fromY = 0, toX = 1, toY = 1)).state
        assertThat(stateUnknown.currentTurn).isEqualTo("Alice")

        val stateWrongTurn = turnService.handleMove(gameState,
            Move("Bob", type = UnitType.INFANTRY, fromX = 5, fromY = 5, toX = 4, toY = 4)).state
        assertThat(stateWrongTurn.currentTurn).isEqualTo("Alice")

        boardService.initializeGame(gameState)
        val stateAfterInit = boardService.resetToStartCondition(gameState)
        assertThat(stateAfterInit.status).isEqualTo(GameStatus.IN_PROGRESS)
    }

    @Test
    fun `test reset with existing players`() {
        playerService.handleJoin(gameState, "Alice")
        val state = boardService.resetToStartCondition(gameState)
        assertThat(state.status).isEqualTo(GameStatus.IN_PROGRESS)
        assertThat(state.currentTurn).isEqualTo("Alice")
        assertThat(state.units).isNotEmpty()
    }

    @Test
    fun `test combat removes losing unit and winner advances`() {
        playerService.handleJoin(gameState, "Alice")
        playerService.handleJoin(gameState, "Bob")
        seedDualValleyCombatUnits()

        val aliceInfantry = gameState.units.first { it.player == "Alice" && it.type == UnitType.INFANTRY }
        val bobCavalry    = gameState.units.first { it.player == "Bob"   && it.type == UnitType.CAVALRY  }

        bobCavalry.x = aliceInfantry.x
        bobCavalry.y = aliceInfantry.y + 1

        turnService.handleMove(gameState, Move(
            player = "Alice", type = UnitType.INFANTRY,
            fromX = aliceInfantry.x, fromY = aliceInfantry.y,
            toX = bobCavalry.x, toY = bobCavalry.y
        ))

        assertThat(gameState.units.none { it.player == "Bob" && it.type == UnitType.CAVALRY }).isTrue()
        val aliceAfter = gameState.units.first { it.player == "Alice" && it.type == UnitType.INFANTRY }
        assertThat(aliceAfter.x).isEqualTo(bobCavalry.x)
        assertThat(aliceAfter.y).isEqualTo(bobCavalry.y)
    }

    @Test
    fun `match ends as draw when both bases are destroyed simultaneously`() {
        boardService.initializeGame(gameState)
        playerService.handleJoin(gameState, "Alice")
        playerService.handleJoin(gameState, "Bob")
        seedDualValleyCombatUnits()

        gameState.units.removeIf { it.type == UnitType.BASE }

        val aliceArcher = gameState.units.first { it.player == "Alice" && it.type == UnitType.ARCHER }
        turnService.handleMove(gameState, Move(
            player = "Alice", type = UnitType.ARCHER,
            fromX = aliceArcher.x, fromY = aliceArcher.y,
            toX = aliceArcher.x, toY = aliceArcher.y + 1
        ))

        assertThat(gameState.status).isEqualTo(GameStatus.FINISHED)
        assertThat(gameState.winner).isNull()
        assertThat(gameState.currentTurn).isNull()
    }

    @Test
    fun `match continues when both bases still stand after combat`() {
        boardService.initializeGame(gameState)
        playerService.handleJoin(gameState, "Alice")
        playerService.handleJoin(gameState, "Bob")
        seedDualValleyCombatUnits()

        val aliceInfantry = gameState.units.first { it.player == "Alice" && it.type == UnitType.INFANTRY }
        val bobCavalry    = gameState.units.first { it.player == "Bob"   && it.type == UnitType.CAVALRY  }

        bobCavalry.x = aliceInfantry.x
        bobCavalry.y = aliceInfantry.y + 1

        turnService.handleMove(gameState, Move("Alice", UnitType.ARCHER, 1, 2, 1, 3))
        turnService.handleMove(gameState, Move("Alice", UnitType.CAVALRY, 3, 2, 3, 3))
        turnService.handleMove(gameState, Move(
            player = "Alice", type = UnitType.INFANTRY,
            fromX = aliceInfantry.x, fromY = aliceInfantry.y,
            toX = bobCavalry.x, toY = bobCavalry.y
        ))
        turnService.endTurn(gameState, "Alice")

        assertThat(gameState.status).isEqualTo(GameStatus.IN_PROGRESS)
        assertThat(gameState.winner).isNull()
        assertThat(gameState.currentTurn).isEqualTo("Bob")
    }

    @Test
    fun `non-combat move does not end the game`() {
        boardService.initializeGame(gameState)
        playerService.handleJoin(gameState, "Alice")
        playerService.handleJoin(gameState, "Bob")
        seedDualValleyCombatUnits()

        val aliceArcher = gameState.units.first { it.player == "Alice" && it.type == UnitType.ARCHER }
        turnService.handleMove(gameState, Move(
            player = "Alice", type = UnitType.ARCHER,
            fromX = aliceArcher.x, fromY = aliceArcher.y,
            toX = aliceArcher.x, toY = aliceArcher.y + 1
        ))
        turnService.handleMove(gameState, Move("Alice", UnitType.INFANTRY, 2, 3, 2, 4))
        turnService.handleMove(gameState, Move("Alice", UnitType.CAVALRY, 3, 2, 3, 3))
        turnService.endTurn(gameState, "Alice")

        assertThat(gameState.status).isEqualTo(GameStatus.IN_PROGRESS)
        assertThat(gameState.winner).isNull()
        assertThat(gameState.currentTurn).isEqualTo("Bob")
    }

    @Test
    fun `player can move onto own skeleton field and absorbs the skeleton`() {
        boardService.initializeGame(gameState)
        playerService.handleJoin(gameState, "Alice")
        playerService.handleJoin(gameState, "Bob")
        seedDualValleyCombatUnits()

        val aliceInfantry = gameState.units.first { it.player == "Alice" && it.type == UnitType.INFANTRY }
        val targetX = aliceInfantry.x
        val targetY = aliceInfantry.y + 1
        gameState.units.add(GameUnit("Alice", targetX, targetY, UnitType.SKELETON))

        turnService.handleMove(gameState, Move(
            player = "Alice", type = UnitType.INFANTRY,
            fromX = aliceInfantry.x, fromY = aliceInfantry.y,
            toX = targetX, toY = targetY
        ))

        val movedInfantry = gameState.units.first { it.player == "Alice" && it.type == UnitType.INFANTRY }
        assertThat(movedInfantry.x).isEqualTo(targetX)
        assertThat(movedInfantry.y).isEqualTo(targetY)
        assertThat(gameState.units.none { it.x == targetX && it.y == targetY && it.type == UnitType.SKELETON }).isTrue()
    }

    @Test
    fun `base unit is spawned for each player at configured position when game starts`() {
        boardService.initializeGame(gameState)
        playerService.handleJoin(gameState, "Alice")
        playerService.handleJoin(gameState, "Bob")
        seedDualValleyCombatUnits()

        val bases = gameState.units.filter { it.type == UnitType.BASE }
        assertThat(bases).hasSize(2)

        val aliceBase = bases.first { it.player == "Alice" }
        val bobBase   = bases.first { it.player == "Bob" }

        assertThat(aliceBase.x).isEqualTo(BoardService.BASE_POSITION_P1.first)
        assertThat(aliceBase.y).isEqualTo(BoardService.BASE_POSITION_P1.second)
        assertThat(bobBase.x).isEqualTo(BoardService.BASE_POSITION_P2.first)
        assertThat(bobBase.y).isEqualTo(BoardService.BASE_POSITION_P2.second)
    }

    @Test
    fun `move attempt with BASE type is rejected and state unchanged`() {
        boardService.initializeGame(gameState)
        playerService.handleJoin(gameState, "Alice")
        playerService.handleJoin(gameState, "Bob")
        seedDualValleyCombatUnits()

        val aliceBase = gameState.units.first { it.player == "Alice" && it.type == UnitType.BASE }
        val turnBefore = gameState.currentTurn

        turnService.handleMove(gameState, Move(
            player = "Alice",
            type = UnitType.BASE,
            fromX = aliceBase.x,
            fromY = aliceBase.y,
            toX = aliceBase.x + 1,
            toY = aliceBase.y
        ))

        val aliceBaseAfter = gameState.units.first { it.player == "Alice" && it.type == UnitType.BASE }
        assertThat(aliceBaseAfter.x).isEqualTo(aliceBase.x)
        assertThat(aliceBaseAfter.y).isEqualTo(aliceBase.y)
        assertThat(gameState.currentTurn).isEqualTo(turnBefore)
    }

    @Test
    fun `regular units are still spawned alongside BASE`() {
        boardService.initializeGame(gameState)
        playerService.handleJoin(gameState, "Alice")
        playerService.handleJoin(gameState, "Bob")
        seedDualValleyCombatUnits()

        val regularTypes = listOf(UnitType.ARCHER, UnitType.INFANTRY, UnitType.CAVALRY)
        regularTypes.forEach { type ->
            val aliceUnit = gameState.units.firstOrNull { it.player == "Alice" && it.type == type }
            val bobUnit   = gameState.units.firstOrNull { it.player == "Bob"   && it.type == type }
            assertThat(aliceUnit).withFailMessage("Alice should still have a $type").isNotNull
            assertThat(bobUnit).withFailMessage("Bob should still have a $type").isNotNull
        }
    }

    @Test
    fun `winner is set when attacker reaches opponent base`() {
        boardService.initializeGame(gameState)
        playerService.handleJoin(gameState, "Alice")
        playerService.handleJoin(gameState, "Bob")
        seedDualValleyCombatUnits()

        val aliceInfantry = gameState.units.first { it.player == "Alice" && it.type == UnitType.INFANTRY }
        val bobBase       = gameState.units.first { it.player == "Bob"   && it.type == UnitType.BASE }

        bobBase.x = aliceInfantry.x
        bobBase.y = aliceInfantry.y + 1

        turnService.handleMove(gameState, Move(
            player = "Alice", type = UnitType.INFANTRY,
            fromX = aliceInfantry.x, fromY = aliceInfantry.y,
            toX = bobBase.x, toY = bobBase.y
        ))

        assertThat(gameState.status).isEqualTo(GameStatus.FINISHED)
        assertThat(gameState.winner).isEqualTo("Alice")
        assertThat(gameState.currentTurn).isNull()
        assertThat(gameState.units.any { it.player == "Bob" && it.type == UnitType.BASE }).isFalse()

        val aliceAfter = gameState.units.first { it.player == "Alice" && it.type == UnitType.INFANTRY }
        assertThat(aliceAfter.x).isEqualTo(bobBase.x)
        assertThat(aliceAfter.y).isEqualTo(bobBase.y)
    }

    @Test
    fun `regular unit killed by combat does NOT end the match`() {
        boardService.initializeGame(gameState)
        playerService.handleJoin(gameState, "Alice")
        playerService.handleJoin(gameState, "Bob")
        seedDualValleyCombatUnits()

        gameState.units.removeIf {
            it.player == "Alice" && it.type != UnitType.INFANTRY && it.type != UnitType.BASE
        }
        gameState.units.removeIf {
            it.player == "Bob" && it.type != UnitType.CAVALRY && it.type != UnitType.BASE
        }

        val aliceInfantry = gameState.units.first { it.player == "Alice" && it.type == UnitType.INFANTRY }
        val bobCavalry    = gameState.units.first { it.player == "Bob"   && it.type == UnitType.CAVALRY }

        bobCavalry.x = aliceInfantry.x
        bobCavalry.y = aliceInfantry.y + 1

        turnService.handleMove(gameState, Move(
            player = "Alice", type = UnitType.INFANTRY,
            fromX = aliceInfantry.x, fromY = aliceInfantry.y,
            toX = bobCavalry.x, toY = bobCavalry.y
        ))

        assertThat(gameState.status).isEqualTo(GameStatus.IN_PROGRESS)
        assertThat(gameState.winner).isNull()
        val bobRegularUnits = gameState.units.filter { it.player == "Bob" && it.type != UnitType.BASE }
        assertThat(bobRegularUnits).isEmpty()
        assertThat(gameState.units.any { it.player == "Bob" && it.type == UnitType.BASE }).isTrue
    }

    @Test
    fun `player receives starting gold on join`() {
        playerService.handleJoin(gameState, "Alice")

        val player = gameState.players.first { it.name == "Alice" }
        assertThat(player.gold).isEqualTo(EconomyService.STARTING_GOLD)
    }

    @Test
    fun `test applyEconomy - normaler Abzug`() {
        boardService.initializeGame(gameState)
        playerService.handleJoin(gameState, "Alice")
        playerService.handleJoin(gameState, "Bob")
        seedDualValleyCombatUnits()

        gameState.players.forEach { it.gold = 20 }

        val aliceInf = gameState.units.first { it.player == "Alice" && it.type == UnitType.INFANTRY }
        turnService.handleMove(gameState,
            Move("Alice", UnitType.INFANTRY, aliceInf.x, aliceInf.y, aliceInf.x, aliceInf.y + 1))
        turnService.endTurn(gameState, "Alice")

        assertThat(gameState.players.first { it.name == "Alice" }.gold).isEqualTo(16)

        val bobInf = gameState.units.first { it.player == "Bob" && it.type == UnitType.INFANTRY }
        turnService.handleMove(gameState,
            Move("Bob", UnitType.INFANTRY, bobInf.x, bobInf.y, bobInf.x, bobInf.y + 1))
        turnService.endTurn(gameState, "Bob")

        assertThat(gameState.players.first { it.name == "Bob" }.gold).isEqualTo(15)
    }

    @Test
    fun `test applyEconomy - Grenzfall exakt 0`() {
        boardService.initializeGame(gameState)
        playerService.handleJoin(gameState, "Alice")
        playerService.handleJoin(gameState, "Bob")
        seedDualValleyCombatUnits()

        gameState.players.forEach { it.gold = 4 }

        val aliceInf = gameState.units.first { it.player == "Alice" && it.type == UnitType.INFANTRY }
        turnService.handleMove(gameState,
            Move("Alice", UnitType.INFANTRY, aliceInf.x, aliceInf.y, aliceInf.x, aliceInf.y + 1))
        turnService.endTurn(gameState, "Alice")

        val alice = gameState.players.first { it.name == "Alice" }
        assertThat(alice.gold).isEqualTo(0)
        assertThat(gameState.units.count { it.player == "Alice" }).isEqualTo(4)
    }

    @Test
    fun `test applyEconomy - Insolvenz mit Unit-Verlust`() {
        boardService.initializeGame(gameState)
        playerService.handleJoin(gameState, "Alice")
        playerService.handleJoin(gameState, "Bob")
        seedDualValleyCombatUnits()

        gameState.players.first { it.name == "Alice" }.gold = 3
        gameState.players.first { it.name == "Bob" }.gold = 20

        val aliceInf = gameState.units.first { it.player == "Alice" && it.type == UnitType.INFANTRY }
        turnService.handleMove(gameState,
            Move("Alice", UnitType.INFANTRY, aliceInf.x, aliceInf.y, aliceInf.x, aliceInf.y + 1))
        turnService.endTurn(gameState, "Alice")

        val alice = gameState.players.first { it.name == "Alice" }
        assertThat(alice.gold).isEqualTo(0)

        val aliceUnits = gameState.units.filter { it.player == "Alice" }
        assertThat(aliceUnits.size).isEqualTo(4)
        val aliceArmy = aliceUnits.filter { it.type != UnitType.BASE }
        assertThat(aliceArmy.all { it.type == UnitType.SKELETON }).isTrue()
    }

    @Test
    fun `test applyEconomy - Insolvenz loest keine Win-Condition aus`() {
        boardService.initializeGame(gameState)
        playerService.handleJoin(gameState, "Alice")
        playerService.handleJoin(gameState, "Bob")
        seedDualValleyCombatUnits()

        gameState.players.first { it.name == "Alice" }.gold = 5
        gameState.players.first { it.name == "Bob" }.gold = 20

        val aliceInf = gameState.units.first { it.player == "Alice" && it.type == UnitType.INFANTRY }
        turnService.handleMove(gameState,
            Move("Alice", UnitType.INFANTRY, aliceInf.x, aliceInf.y, aliceInf.x, aliceInf.y + 1))
        turnService.endTurn(gameState, "Alice")

        assertThat(gameState.status).isEqualTo(GameStatus.IN_PROGRESS)
        assertThat(gameState.winner).isNull()
    }

    @Test
    fun `test applyEconomy - Farm-Einkommen verhindert Insolvenz`() {
        boardService.initializeGame(gameState)
        playerService.handleJoin(gameState, "Alice")
        playerService.handleJoin(gameState, "Bob")
        seedDualValleyCombatUnits()

        val alice = gameState.players.first { it.name == "Alice" }
        val bob = gameState.players.first { it.name == "Bob" }
        alice.farms = 1
        alice.gold = 2
        bob.gold = 10

        val aliceInf = gameState.units.first { it.player == "Alice" && it.type == UnitType.INFANTRY }
        turnService.handleMove(gameState,
            Move("Alice", UnitType.INFANTRY, aliceInf.x, aliceInf.y, aliceInf.x, aliceInf.y + 1))
        turnService.endTurn(gameState, "Alice")

        assertThat(alice.gold).isEqualTo(1)
        assertThat(alice.farms).isEqualTo(1)
        assertThat(gameState.units.filter { it.player == "Alice" }.all { it.type != UnitType.SKELETON }).isTrue()
    }

    @Test
    fun `field income added on top of farm income`() {
        val state = GameState().apply {
            status = GameStatus.IN_PROGRESS
            currentTurn = "Alice"
            players.add(Player(name = "Alice", gold = 0, farms = 1))
            fields.addAll(listOf(
                Field(0, 0, owner = "Alice"),
                Field(0, 1, owner = "Alice"),
                Field(0, 2, owner = "Alice")
            ))
        }

        turnService.endTurn(state, "Alice")

        assertEquals(6, state.players[0].gold)
    }

    @Test
    fun `field income works with zero farms`() {
        val state = GameState().apply {
            status = GameStatus.IN_PROGRESS
            currentTurn = "Alice"
            players.add(Player(name = "Alice", gold = 0, farms = 0))
            fields.addAll(List(5) { Field(0, it, owner = "Alice") })
        }

        turnService.endTurn(state, "Alice")

        assertEquals(5, state.players[0].gold)
    }

    @Test
    fun `field income works with zero fields and zero farms`() {
        val state = GameState().apply {
            status = GameStatus.IN_PROGRESS
            currentTurn = "Alice"
            players.add(Player(name = "Alice", gold = 0, farms = 0))
        }

        turnService.endTurn(state, "Alice")

        assertEquals(0, state.players[0].gold)
    }

    @Test
    fun `field income excludes skeleton fields`() {
        val state = GameState().apply {
            status = GameStatus.IN_PROGRESS
            currentTurn = "Alice"
            players.add(Player(name = "Alice", gold = 0, farms = 0))
            fields.add(Field(0, 0, owner = "Alice"))
            fields.add(Field(0, 1, owner = "Alice"))
            fields.add(Field(0, 2, owner = "Alice", isSkeleton = true))
        }

        turnService.endTurn(state, "Alice")

        assertEquals(2, state.players[0].gold)
    }

    @Test
    fun `field income is zero when all fields are skeleton`() {
        val state = GameState().apply {
            status = GameStatus.IN_PROGRESS
            currentTurn = "Alice"
            players.add(Player(name = "Alice", gold = 0, farms = 1))
            fields.addAll(List(3) { Field(0, it, owner = "Alice", isSkeleton = true) })
        }

        turnService.endTurn(state, "Alice")

        assertEquals(3, state.players[0].gold)
    }

    @Test
    fun `recomputePlayerStats income drops when a field becomes skeleton`() {
        val state = GameState().apply {
            players.add(Player(name = "Alice", farms = 0))
            fields.add(Field(0, 0, owner = "Alice"))
            fields.add(Field(0, 1, owner = "Alice"))
            fields.add(Field(0, 2, owner = "Alice"))
        }

        economyService.recomputePlayerStats(state)
        val incomeBefore = state.players[0].income

        state.fields[1].isSkeleton = true
        economyService.recomputePlayerStats(state)
        val incomeAfter = state.players[0].income

        assertEquals(3, incomeBefore)
        assertEquals(2, incomeAfter)
    }

    @Test
    fun `recomputePlayerStats sets income from fields and farms`() {
        val state = GameState().apply {
            players.add(Player(name = "Alice", farms = 2))
            fields.addAll(List(4) { Field(0, it, owner = "Alice") })
        }
        economyService.recomputePlayerStats(state)

        assertEquals(4 + 2 * 3, state.players[0].income)
        assertEquals(0, state.players[0].upkeep)
    }

    @Test
    fun `recomputePlayerStats sets upkeep based on living non-base units`() {
        val state = GameState().apply {
            players.add(Player(name = "Alice"))
            units.addAll(listOf(
                GameUnit(player = "Alice", type = UnitType.INFANTRY, x = 0, y = 0),
                GameUnit(player = "Alice", type = UnitType.CAVALRY, x = 0, y = 1),
                GameUnit(player = "Alice", type = UnitType.BASE, x = 0, y = 2),
                GameUnit(player = "Alice", type = UnitType.SKELETON, x = 0, y = 3)
            ))
        }
        economyService.recomputePlayerStats(state)

        assertEquals(7, state.players[0].upkeep)
    }

    @Test
    fun `after buyFarm the broadcasted state contains updated income`() {
        val state = GameState().apply {
            players.add(Player(name = "Alice", gold = 10, farms = 0))
        }

        state.players[0].farms += 1
        state.players[0].gold -= 10
        economyService.recomputePlayerStats(state)

        assertEquals(3, state.players[0].income)
    }

    @Test
    fun `after field conquest income shifts from old to new owner`() {
        val state = GameState().apply {
            players.add(Player(name = "Alice"))
            players.add(Player(name = "Bob"))
            fields.add(Field(0, 0, owner = "Bob"))
        }

        state.fields[0].owner = "Alice"
        economyService.recomputePlayerStats(state)

        assertEquals(1, state.players.find { it.name == "Alice" }?.income)
        assertEquals(0, state.players.find { it.name == "Bob" }?.income)
    }

    @Test
    fun `after endTurn with insolvency upkeep drops to zero`() {
        val state = GameState().apply {
            status = GameStatus.IN_PROGRESS
            players.add(Player(name = "Alice", gold = 0))
            players.add(Player(name = "Bob", gold = 10))
            currentTurn = "Alice"
            units.add(GameUnit(player = "Alice", type = UnitType.INFANTRY, x = 0, y = 0))
        }

        turnService.endTurn(state, "Alice")
        economyService.recomputePlayerStats(state)

        assertEquals(0, state.players[0].upkeep)
        assertEquals(UnitType.SKELETON, state.units[0].type)
        assertEquals("Bob", state.currentTurn)
    }

    @Test
    fun `endTurn applies economy only to the ending player`() {
        val state = GameState().apply {
            players.addAll(listOf(
                Player(name = "Alice", gold = 0, farms = 1),
                Player(name = "Bob", gold = 0, farms = 1)
            ))
            currentTurn = "Alice"
            status = GameStatus.IN_PROGRESS
        }
        turnService.endTurn(state, "Alice")

        assertEquals(3, state.players[0].gold)
        assertEquals(0, state.players[1].gold)
        assertEquals("Bob", state.currentTurn)
    }

    @Test
    fun `each player gets exactly one income per full round in TRIAD_OUTPOST`() {
        val state = GameState().apply {
            gameMode = GameMode.TRIAD_OUTPOST
            players.addAll(listOf(
                Player(name = "Alice", gold = 0, farms = 1),
                Player(name = "Bob",   gold = 0, farms = 1),
                Player(name = "Carol", gold = 0, farms = 1)
            ))
            currentTurn = "Alice"
            status = GameStatus.IN_PROGRESS
        }
        turnService.endTurn(state, "Alice")
        turnService.endTurn(state, "Bob")
        turnService.endTurn(state, "Carol")

        assertEquals(3, state.players[0].gold)
        assertEquals(3, state.players[1].gold)
        assertEquals(3, state.players[2].gold)
    }

    @Test
    fun `insolvency only affects the ending player`() {
        val state = GameState().apply {
            players.addAll(listOf(
                Player(name = "Alice", gold = 0, farms = 0),
                Player(name = "Bob",   gold = 100, farms = 0)
            ))
            units.addAll(listOf(
                GameUnit(player = "Alice", type = UnitType.INFANTRY, x = 0, y = 0),
                GameUnit(player = "Bob",   type = UnitType.INFANTRY, x = 1, y = 1)
            ))
            currentTurn = "Alice"
            status = GameStatus.IN_PROGRESS
        }
        turnService.endTurn(state, "Alice")

        assertEquals(0, state.players[0].gold)
        assertEquals(UnitType.SKELETON, state.units[0].type)
        assertEquals(100, state.players[1].gold)
        assertEquals(UnitType.INFANTRY, state.units[1].type)
    }

    @Test
    fun `each player gets exactly one income per full round in BATTLEFIELD_PEAKS`() {
        val state = GameState().apply {
            gameMode = GameMode.BATTLEFIELD_PEAKS
            players.addAll(listOf(
                Player(name = "Alice", gold = 0, farms = 1),
                Player(name = "Bob",   gold = 0, farms = 1),
                Player(name = "Carol", gold = 0, farms = 1),
                Player(name = "Dave",  gold = 0, farms = 1)
            ))
            currentTurn = "Alice"
            status = GameStatus.IN_PROGRESS
        }
        turnService.endTurn(state, "Alice")
        turnService.endTurn(state, "Bob")
        turnService.endTurn(state, "Carol")
        turnService.endTurn(state, "Dave")

        assertEquals(3, state.players[0].gold)
        assertEquals(3, state.players[1].gold)
        assertEquals(3, state.players[2].gold)
        assertEquals(3, state.players[3].gold)
    }

    @Test
    fun `disconnect removes player but game continues cleanly`() {
        val state = GameState().apply {
            gameMode = GameMode.TRIAD_OUTPOST
            players.addAll(listOf(
                Player(name = "Alice", sessionId = "sess-alice", gold = 0, farms = 1),
                Player(name = "Bob",   sessionId = "sess-bob",   gold = 0, farms = 1),
                Player(name = "Carol", sessionId = "sess-carol", gold = 0, farms = 1)
            ))
            units.addAll(listOf(
                GameUnit(player = "Alice", type = UnitType.BASE, x = 0, y = 0),
                GameUnit(player = "Bob",   type = UnitType.BASE, x = 5, y = 5),
                GameUnit(player = "Carol", type = UnitType.BASE, x = 10, y = 10)
            ))
            currentTurn = "Bob"
            status = GameStatus.IN_PROGRESS
        }

        playerService.handleDisconnect(state, "sess-alice")

        assertEquals(GameStatus.IN_PROGRESS, state.status)
        assertEquals(0, state.players.first { it.name == "Bob" }.gold)
        assertEquals(0, state.players.first { it.name == "Carol" }.gold)
    }

    @Test
    fun `claimCheatGift adds positive delta and sets pendingGift`() {
        val state = GameState().apply {
            players.addAll(listOf(
                Player(name = "Alice", gold = 5),
                Player(name = "Bob", gold = 0),
                Player(name = "Carol", gold = 0)
            ))
        }

        cheatGiftService.claimCheatGift(state, "Alice", 7)

        val alice = state.players.first { it.name == "Alice" }
        assertEquals(12, alice.gold)
        assertTrue(alice.hasUsedGift)
        assertNotNull(state.pendingGift)
        assertEquals("Alice", state.pendingGift?.ownerName)
        assertEquals(7, state.pendingGift?.delta)
        assertEquals(2, state.pendingGift?.pendingDecisions)
    }

    @Test
    fun `claimCheatGift caps gold at zero on negative delta`() {
        val state = GameState().apply {
            players.addAll(listOf(
                Player(name = "Alice", gold = 3),
                Player(name = "Bob", gold = 0)
            ))
        }

        cheatGiftService.claimCheatGift(state, "Alice", -10)

        assertEquals(0, state.players.first { it.name == "Alice" }.gold)
    }

    @Test
    fun `claimCheatGift does not cap when negative delta fits`() {
        val state = GameState().apply {
            players.addAll(listOf(
                Player(name = "Alice", gold = 5),
                Player(name = "Bob", gold = 0)
            ))
        }

        cheatGiftService.claimCheatGift(state, "Alice", -3)

        assertEquals(2, state.players.first { it.name == "Alice" }.gold)
    }

    @Test
    fun `claimCheatGift sets pendingDecisions to player count minus one`() {
        val state = GameState().apply {
            players.addAll(listOf(
                Player(name = "Alice", gold = 0),
                Player(name = "Bob", gold = 0),
                Player(name = "Carol", gold = 0),
                Player(name = "Dave", gold = 0)
            ))
        }

        cheatGiftService.claimCheatGift(state, "Alice", 5)

        assertEquals(3, state.pendingGift?.pendingDecisions)
    }

    @Test
    fun `respondCheatSteal accept transfers delta and clears pendingGift`() {
        val state = GameState().apply {
            players.addAll(listOf(
                Player(name = "Alice", gold = 12, hasUsedGift = true),
                Player(name = "Bob", gold = 5)
            ))
            pendingGift = PendingGift(ownerName = "Alice", delta = 7, pendingDecisions = 1)
        }

        cheatGiftService.respondCheatSteal(state, "Bob", true)

        assertEquals(5, state.players.first { it.name == "Alice" }.gold)
        assertEquals(12, state.players.first { it.name == "Bob" }.gold)
        assertNull(state.pendingGift)
    }

    @Test
    fun `respondCheatSteal accept caps stealer gold at zero on negative delta`() {
        val state = GameState().apply {
            players.addAll(listOf(
                Player(name = "Alice", gold = 0, hasUsedGift = true),
                Player(name = "Bob", gold = 2)
            ))
            pendingGift = PendingGift(ownerName = "Alice", delta = -5, pendingDecisions = 1)
        }

        cheatGiftService.respondCheatSteal(state, "Bob", true)

        assertEquals(5, state.players.first { it.name == "Alice" }.gold)
        assertEquals(0, state.players.first { it.name == "Bob" }.gold)
        assertNull(state.pendingGift)
    }

    @Test
    fun `respondCheatSteal decline decrements pendingDecisions when others remain`() {
        val state = GameState().apply {
            players.addAll(listOf(
                Player(name = "Alice", gold = 10, hasUsedGift = true),
                Player(name = "Bob", gold = 5),
                Player(name = "Carol", gold = 5)
            ))
            pendingGift = PendingGift(ownerName = "Alice", delta = 5, pendingDecisions = 2)
        }

        cheatGiftService.respondCheatSteal(state, "Bob", false)

        assertNotNull(state.pendingGift)
        assertEquals(1, state.pendingGift?.pendingDecisions)
        assertEquals(10, state.players.first { it.name == "Alice" }.gold)
    }

    @Test
    fun `respondCheatSteal decline clears pendingGift when last decision`() {
        val state = GameState().apply {
            players.addAll(listOf(
                Player(name = "Alice", gold = 10, hasUsedGift = true),
                Player(name = "Bob", gold = 5)
            ))
            pendingGift = PendingGift(ownerName = "Alice", delta = 5, pendingDecisions = 1)
        }

        cheatGiftService.respondCheatSteal(state, "Bob", false)

        assertNull(state.pendingGift)
        assertEquals(10, state.players.first { it.name == "Alice" }.gold)
    }

    @Test
    fun `hardDelete clears pendingGift when owner disconnects`() {
        val state = GameState().apply {
            gameMode = GameMode.TRIAD_OUTPOST
            status = GameStatus.IN_PROGRESS
            players.addAll(listOf(
                Player(name = "Alice", sessionId = "sess-alice", gold = 10, hasUsedGift = true),
                Player(name = "Bob", sessionId = "sess-bob", gold = 5),
                Player(name = "Carol", sessionId = "sess-carol", gold = 5)
            ))
            units.addAll(listOf(
                GameUnit(player = "Alice", type = UnitType.BASE, x = 0, y = 0),
                GameUnit(player = "Bob", type = UnitType.BASE, x = 5, y = 5),
                GameUnit(player = "Carol", type = UnitType.BASE, x = 10, y = 10)
            ))
            pendingGift = PendingGift(ownerName = "Alice", delta = 5, pendingDecisions = 2)
        }

        val alice = state.players.first { it.name == "Alice" }
        playerService.handleDisconnect(state, "sess-alice")
        playerService.hardDelete(state, alice)

        assertNull(state.pendingGift)
    }

    @Test
    fun `hardDelete decrements pendingDecisions when stealer disconnects`() {
        val state = GameState().apply {
            gameMode = GameMode.TRIAD_OUTPOST
            status = GameStatus.IN_PROGRESS
            players.addAll(listOf(
                Player(name = "Alice", sessionId = "sess-alice", gold = 10, hasUsedGift = true),
                Player(name = "Bob", sessionId = "sess-bob", gold = 5),
                Player(name = "Carol", sessionId = "sess-carol", gold = 5)
            ))
            units.addAll(listOf(
                GameUnit(player = "Alice", type = UnitType.BASE, x = 0, y = 0),
                GameUnit(player = "Bob", type = UnitType.BASE, x = 5, y = 5),
                GameUnit(player = "Carol", type = UnitType.BASE, x = 10, y = 10)
            ))
            pendingGift = PendingGift(ownerName = "Alice", delta = 5, pendingDecisions = 2)
        }

        val bob = state.players.first { it.name == "Bob" }
        playerService.handleDisconnect(state, "sess-bob")
        playerService.hardDelete(state, bob)

        assertNotNull(state.pendingGift)
        assertEquals(1, state.pendingGift?.pendingDecisions)
    }

    @Test
    fun `hardDelete clears pendingGift when last stealer disconnects`() {
        val state = GameState().apply {
            gameMode = GameMode.TRIAD_OUTPOST
            status = GameStatus.IN_PROGRESS
            players.addAll(listOf(
                Player(name = "Alice", sessionId = "sess-alice", gold = 10, hasUsedGift = true),
                Player(name = "Bob", sessionId = "sess-bob", gold = 5)
            ))
            units.addAll(listOf(
                GameUnit(player = "Alice", type = UnitType.BASE, x = 0, y = 0),
                GameUnit(player = "Bob", type = UnitType.BASE, x = 5, y = 5)
            ))
            pendingGift = PendingGift(ownerName = "Alice", delta = 5, pendingDecisions = 1)
        }

        val bob = state.players.first { it.name == "Bob" }
        playerService.handleDisconnect(state, "sess-bob")
        playerService.hardDelete(state, bob)

        assertNull(state.pendingGift)
    }

    @Test
    fun `base loss in multiplayer eliminates player and frees fields`() {
        val state = GameState().apply {
            status = GameStatus.IN_PROGRESS
            gameMode = GameMode.TRIAD_OUTPOST
            players.addAll(listOf(Player("Alice", "s1"), Player("Bob", "s2"), Player("Carol", "s3")))
            currentTurn = "Alice"

            units.add(GameUnit("Alice", 0, 0, UnitType.BASE))
            units.add(GameUnit("Alice", 1, 0, UnitType.INFANTRY))
            units.add(GameUnit("Bob", 2, 0, UnitType.BASE))
            units.add(GameUnit("Bob", 3, 0, UnitType.ARCHER))
            units.add(GameUnit("Carol", 5, 5, UnitType.BASE))

            fields.add(Field(1, 0).apply { owner = "Alice" })
            fields.add(Field(2, 0).apply { owner = "Bob" })
            fields.add(Field(3, 0).apply { owner = "Bob" })
        }

        turnService.handleMove(state, Move("Alice", UnitType.INFANTRY, 1, 0, 2, 0))

        assertEquals(2, state.players.size)
        assertTrue(state.players.none { it.name == "Bob" })
        assertTrue(state.units.none { it.player == "Bob" })
        assertNull(state.fields.first { it.x == 3 && it.y == 0 }.owner)
        assertEquals(GameStatus.IN_PROGRESS, state.status)
    }

    @Test
    fun `disconnect of active player passes turn and eliminates player`() {
        val state = GameState().apply {
            status = GameStatus.IN_PROGRESS
            gameMode = GameMode.TRIAD_OUTPOST
            players.addAll(listOf(Player("Alice", "s1"), Player("Bob", "s2"), Player("Carol", "s3")))
            currentTurn = "Bob"

            units.add(GameUnit("Alice", 0, 0, UnitType.BASE))
            units.add(GameUnit("Bob", 1, 1, UnitType.BASE))
            units.add(GameUnit("Carol", 2, 2, UnitType.BASE))

            fields.add(Field(1, 1).apply { owner = "Bob" })
        }

        val bob = state.players.first { it.sessionId == "s2" }
        playerService.handleDisconnect(state, "s2")
        playerService.hardDelete(state, bob)

        assertEquals(2, state.players.size)
        assertEquals("Carol", state.currentTurn)
        assertTrue(state.units.none { it.player == "Bob" })
        assertNull(state.fields.first { it.x == 1 && it.y == 1 }.owner)
        assertEquals(GameStatus.IN_PROGRESS, state.status)
    }
}
