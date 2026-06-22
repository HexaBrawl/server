package at.aau.hexabrawl.websocketserver.service

import at.aau.hexabrawl.websocketserver.model.GameState
import at.aau.hexabrawl.websocketserver.model.Move
import at.aau.hexabrawl.websocketserver.model.Player
import at.aau.hexabrawl.websocketserver.model.PlayerColor
import at.aau.hexabrawl.websocketserver.model.UnitType
import org.springframework.stereotype.Service

/**
 * Delegations-Fassade ueber die spezialisierten Domain-Services
 * (Board, Player, Turn, Economy, Cheat, Connectivity).
 *
 * Existiert vor allem als stabile API fuer Controller und Tests, die nach
 * dem Service-Split nicht alle einzeln umgestellt werden mussten. Haelt
 * zusaetzlich eine globale [gameState]-Instanz fuer Single-Game-Test-Pfade.
 *
 * Neue Production-Aufrufer sollten direkt auf den passenden Sub-Service
 * gehen statt ueber GameService.
 */
@Service
class GameService(
    private val connectivityService: ConnectivityService,
    private val economyService: EconomyService,
    private val cheatGiftService: CheatGiftService,
    private val boardService: BoardService,
    private val playerService: PlayerService,
    private val turnService: TurnService
) {

    val gameState = GameState()

    companion object {
        /** Ungenutzte Companions werden nicht entfernt, weil sie noch von Tests benötigt werden.**/
        // veraltet — Mode-spezifische Spielerzahl kommt aus state.gameMode.maxPlayers (#51).
        // Wird noch von einigen Tests referenziert, daher nicht entfernt.
        const val MAX_PLAYERS = 2

        // Bridge zu TurnService.MAX_MOVE_DISTANCE
        const val MAX_MOVE_DISTANCE = TurnService.MAX_MOVE_DISTANCE

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

    /** Bridge zu TurnService.handleMove. */
    fun handleMove(state: GameState, move: Move): GameState = turnService.handleMove(state, move).state
    fun handleMove(move: Move): GameState = handleMove(this.gameState, move)

    /** Bridge zu TurnService.endTurn. */
    fun endTurn(state: GameState, playerName: String): GameState = turnService.endTurn(state, playerName)
    fun endTurn(playerName: String): GameState = endTurn(this.gameState, playerName)

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