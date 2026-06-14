package at.aau.hexabrawl.websocketserver.service

import at.aau.hexabrawl.websocketserver.model.GameState
import at.aau.hexabrawl.websocketserver.model.GameStatus
import at.aau.hexabrawl.websocketserver.model.GameUnit
import at.aau.hexabrawl.websocketserver.model.HexDistance
import at.aau.hexabrawl.websocketserver.model.Move
import at.aau.hexabrawl.websocketserver.model.Player
import at.aau.hexabrawl.websocketserver.model.PlayerColor
import at.aau.hexabrawl.websocketserver.model.UnitType
import org.springframework.stereotype.Service

@Service
class GameService(
    private val combatService: CombatService,
    private val connectivityService: ConnectivityService,
    private val economyService: EconomyService,
    private val cheatGiftService: CheatGiftService,
    private val boardService: BoardService,
    private val playerService: PlayerService
) {

    val gameState = GameState()

    companion object {
        // veraltet — Mode-spezifische Spielerzahl kommt aus state.gameMode.maxPlayers (#51).
        // Wird noch von einigen Tests referenziert, daher nicht entfernt.
        const val MAX_PLAYERS = 2

        // Maximale Hex-Distanz, die eine Einheit pro Zug zuruecklegen darf.
        const val MAX_MOVE_DISTANCE = 2

        // Wirtschaftssystem (#60) — Bridges zu EconomyService fuer Controller + Tests
        const val STARTING_GOLD = EconomyService.STARTING_GOLD
        const val FARM_INCOME_PER_ROUND = EconomyService.FARM_INCOME_PER_ROUND
        const val FIELD_INCOME_PER_ROUND = EconomyService.FIELD_INCOME_PER_ROUND
        const val FARM_BASE_COST = EconomyService.FARM_BASE_COST
        const val FARM_COST_INCREMENT = EconomyService.FARM_COST_INCREMENT


        // Board-Konstanten — Bridges zu BoardService fuer Tests
        const val DUAL_VALLEY_BOARD_ROWS = BoardService.DUAL_VALLEY_BOARD_ROWS
        const val DUAL_VALLEY_BOARD_COLS = BoardService.DUAL_VALLEY_BOARD_COLS
        const val TRIAD_BOARD_ROWS = BoardService.TRIAD_BOARD_ROWS
        const val TRIAD_BOARD_COLS = BoardService.TRIAD_BOARD_COLS
        const val BATTLEFIELD_BOARD_ROWS = BoardService.BATTLEFIELD_BOARD_ROWS
        const val BATTLEFIELD_BOARD_COLS = BoardService.BATTLEFIELD_BOARD_COLS

        // Bridge zu EconomyService.UNIT_PRICE
        const val UNIT_PRICE = EconomyService.UNIT_PRICE

        // Basis-Positionen fuer DUAL_VALLEY
        val BASE_POSITION_P1: Pair<Int, Int> = BoardService.BASE_POSITION_P1
        val BASE_POSITION_P2: Pair<Int, Int> = BoardService.BASE_POSITION_P2

        // Startgebiete DUAL_VALLEY: Basis + 6 angrenzende Felder
        val START_TERRITORY_P1: List<Pair<Int, Int>> = BoardService.START_TERRITORY_P1
        val START_TERRITORY_P2: List<Pair<Int, Int>> = BoardService.START_TERRITORY_P2


        // Liefert die 6 Nachbarfelder eines Hex-Feldes in "odd-q offset" Koordinaten.
        fun hexNeighbors(x: Int, y: Int): List<Pair<Int, Int>> = ConnectivityService.hexNeighbors(x, y)
    }

    /** Bridge zu PlayerService.handleJoin. */
    fun handleJoin(
        state: GameState,
        playerName: String,
        sessionId: String = "",
        color: PlayerColor? = null
    ): GameState = playerService.handleJoin(state, playerName, sessionId, color)

    // Bridge fuer den globalen Single-Game-Pfad (Tests + Backward-Compat).
    fun handleJoin(
        playerName: String,
        sessionId: String = "",
        color: PlayerColor? = null
    ): GameState = handleJoin(this.gameState, playerName, sessionId, color)

    /** Bridge zu PlayerService.isColorTaken. */
    fun isColorTaken(state: GameState, color: PlayerColor): Boolean = playerService.isColorTaken(state, color)
    fun isColorTaken(color: PlayerColor): Boolean = isColorTaken(this.gameState, color)

    /** Bridge zu BoardService.startGame. */
    fun startGame(state: GameState) = boardService.startGame(state)

    fun handleMove(state: GameState, move: Move): GameState = synchronized(state.lock) {
        if (state.status != GameStatus.IN_PROGRESS) return state
        if (move.player != state.currentTurn) return state

        if (move.type == UnitType.BASE) return state

        val unit = state.units.firstOrNull {
            it.player == move.player &&
                    it.type == move.type &&
                    it.type != UnitType.SKELETON &&
                    it.x == move.fromX &&
                    it.y == move.fromY
        } ?: return state

        val distance = HexDistance.between(move.fromX, move.fromY, move.toX, move.toY)
        if (distance == 0 || distance > MAX_MOVE_DISTANCE) return state

        if (unit.hasMovedThisTurn) return state

        val friendlyOnTarget = state.units.any {
            it.x == move.toX && it.y == move.toY && it.player == move.player
        }
        if (friendlyOnTarget) return state

        // Randfeld-Regel: Einheit darf nur auf eigenes oder angrenzendes Feld ziehen.
        val targetField = state.fields.firstOrNull { it.x == move.toX && it.y == move.toY }
        val isOwnField = targetField?.owner == move.player
        val isBorderField = isAdjacentToOwnTerritory(state, move.toX, move.toY, move.player)
        if (!isOwnField && !isBorderField) return state

        // Skelett auf Zielfeld entfernen bevor Combat geprueft wird.
        state.units.removeIf {
            it.x == move.toX && it.y == move.toY && it.type == UnitType.SKELETON
        }

        val enemyOnTarget = state.units.firstOrNull {
            it.x == move.toX && it.y == move.toY &&
                    it.player != move.player &&
                    it.type != UnitType.SKELETON
        }

        if (enemyOnTarget != null) {
            // Basis-Angriff: nicht combat-faehig, direkt zerstoeren.
            if (enemyOnTarget.type == UnitType.BASE) {
                state.units.remove(enemyOnTarget)
                unit.x = move.toX
                unit.y = move.toY
                finishMove(state, unit, move.player)
                checkWinCondition(state)
                return state
            }

            val result = combatService.resolveCombat(unit, enemyOnTarget)
            combatService.applyCombatResult(result, unit, enemyOnTarget)
            if (!result.defenderSurvived) state.units.remove(enemyOnTarget)
            if (!result.attackerSurvived) state.units.remove(unit)
            finishMove(state, unit, move.player)
            checkWinCondition(state)
            return state
        }

        unit.x = move.toX
        unit.y = move.toY

        finishMove(state, unit, move.player)

        checkWinCondition(state)
        return state
    }

    fun handleMove(move: Move): GameState = handleMove(this.gameState, move)

    /** Bridge zu PlayerService.checkWinCondition. */
    internal fun checkWinCondition(state: GameState) = playerService.checkWinCondition(state)

    /**
     * Beendet die Runde des angegebenen Spielers freiwillig.
     */
    fun endTurn(state: GameState, playerName: String): GameState = synchronized(state.lock) {
        if (state.status != GameStatus.IN_PROGRESS) return state
        if (state.currentTurn != playerName) return state
        switchTurn(state)
        return state
    }

    fun endTurn(playerName: String): GameState = endTurn(this.gameState, playerName)

    /**
     * Naechsten Spieler an die Reihe nehmen.
     *
     *  - 2 Spieler: einfache Alternation
     *  - 3/4 Spieler: zyklische Rotation (#51)
     *  - hasMovedThisTurn wird immer zurueckgesetzt (#61)
     *  - Runden-Wrap-Around: Upkeep sobald wieder Spieler 1 dran ist (#60)
     */
    private fun switchTurn(state: GameState) {
        if (state.players.isEmpty()) return

        // Wirtschaft des Spielers, dessen Zug gerade endet
        state.players.firstOrNull { it.name == state.currentTurn }?.let {
            economyService.applyEconomy(state, it)
        }

        if (state.players.size == 2) {
            val p1 = state.players[0]
            val p2 = state.players[1]
            state.currentTurn = if (state.currentTurn == p1.name) p2.name else p1.name
        } else {
            val currentIndex = state.players.indexOfFirst { it.name == state.currentTurn }
            state.currentTurn = if (currentIndex != -1) {
                state.players[(currentIndex + 1) % state.players.size].name
            } else {
                state.players.firstOrNull()?.name
            }
        }

        // Neue Runde: alle Einheiten dürfen wieder ziehen
        state.units.forEach { it.hasMovedThisTurn = false }
    }

    private fun allMovableUnitsHaveMoved(state: GameState, playerName: String): Boolean {
        val movable = state.units.filter {
            it.player == playerName &&
                    it.type != UnitType.SKELETON &&
                    it.type != UnitType.BASE
        }
        return movable.isNotEmpty() && movable.all { it.hasMovedThisTurn }
    }

    /**
     * Wird nach jedem erfolgreichen Move aufgerufen. Markiert die Einheit
     * als bewegt, erobert das Feld und switcht automatisch den Turn wenn
     * alle bewegbaren Einheiten des aktuellen Spielers gezogen haben.
     *
     * Verhalten ist fuer alle Modi (DUAL_VALLEY, TRIAD_OUTPOST,
     * BATTLEFIELD_PEAKS) identisch: der Spieler darf jede seiner
     * bewegbaren Einheiten maximal einmal pro Zug bewegen. Auto-Switch
     * passiert erst, wenn alle bewegbaren Einheiten gezogen haben.
     * Vorzeitig kann der Spieler ueber den /end-turn-Endpoint manuell
     * wechseln.
     */
    private fun finishMove(state: GameState, unit: GameUnit, playerName: String) {
        if (unit in state.units) {
            unit.hasMovedThisTurn = true
            state.fields.firstOrNull { it.x == unit.x && it.y == unit.y }
                ?.let {
                    it.owner = playerName
                    it.isSkeleton = false
                }
        }
        recomputeConnectivity(state)
        if (allMovableUnitsHaveMoved(state, playerName)) {
            switchTurn(state)
        }
    }

    private fun isAdjacentToOwnTerritory(state: GameState, x: Int, y: Int, playerName: String): Boolean {
        return state.fields.any { field ->
            field.owner == playerName &&
                    HexDistance.between(field.x, field.y, x, y) == 1
        }
    }

    /** Bridge zu ConnectivityService — bleibt fuer Backwards-Compat von Tests. */
    fun recomputeConnectivity(state: GameState) = connectivityService.recomputeConnectivity(state)

    // WICHTIG FÜR TEST — nur den aktuellen Stand lesen
    fun getCurrentState(state: GameState): GameState = synchronized(state.lock) {
        return state
    }

    fun getCurrentState(): GameState = getCurrentState(this.gameState)

    /** Bridge zu BoardService.initializeGame. */
    fun initializeGame(state: GameState): GameState = boardService.initializeGame(state)
    fun initializeGame(): GameState = initializeGame(this.gameState)

    /** Bridge zu BoardService.resetToStartCondition. */
    fun resetToStartCondition(state: GameState): GameState = boardService.resetToStartCondition(state)
    fun resetToStartCondition(): GameState = resetToStartCondition(this.gameState)

    /** Bridge zu PlayerService.handleDisconnect. */
    fun handleDisconnect(state: GameState, sessionId: String): GameState = playerService.handleDisconnect(state, sessionId)
    fun handleDisconnect(sessionId: String): GameState = handleDisconnect(this.gameState, sessionId)

    /** Bridge zu PlayerService.hardDelete. */
    internal fun hardDelete(state: GameState, player: Player) = playerService.hardDelete(state, player)

    /** Bridge zu EconomyService.buyUnit. */
    fun buyUnit(
        state: GameState,
        playerName: String,
        type: UnitType,
        x: Int,
        y: Int
    ): GameState = economyService.buyUnit(state, playerName, type, x, y)

    /** Bridge zu EconomyService — Controller + Tests rufen das auf. */
    fun recomputePlayerStats(state: GameState) = economyService.recomputePlayerStats(state)

    /** Bridge zu CheatGiftService.claimCheatGift. */
    fun claimCheatGift(state: GameState, playerName: String, delta: Int): GameState = cheatGiftService.claimCheatGift(state, playerName, delta)

    /** Bridge zu CheatGiftService.respondCheatSteal. */
    fun respondCheatSteal(state: GameState, playerName: String, accept: Boolean): GameState = cheatGiftService.respondCheatSteal(state, playerName, accept)
}