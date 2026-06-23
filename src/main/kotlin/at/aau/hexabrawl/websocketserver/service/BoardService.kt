package at.aau.hexabrawl.websocketserver.service

import at.aau.hexabrawl.websocketserver.model.Field
import at.aau.hexabrawl.websocketserver.model.GameMode
import at.aau.hexabrawl.websocketserver.model.GameState
import at.aau.hexabrawl.websocketserver.model.GameStatus
import at.aau.hexabrawl.websocketserver.model.GameUnit
import at.aau.hexabrawl.websocketserver.model.UnitType
import org.springframework.stereotype.Service

/**
 * Spielfeld-Setup: erzeugt die Felder, weist Startgebiete zu, platziert BASEn.
 *
 * Handhabt die drei Modes (DUAL_VALLEY, TRIAD_OUTPOST, BATTLEFIELD_PEAKS) mit
 * ihren jeweiligen Board-Dimensionen und Startgebiet-Layouts.
 *
 * Wird vom PlayerService aufgerufen, sobald die Mode-Spielerzahl erreicht ist.
 */
@Service
class BoardService {

    companion object {
        // Board-Dimensionen pro Modus
        const val DUAL_VALLEY_BOARD_ROWS = 9
        const val DUAL_VALLEY_BOARD_COLS = 9
        const val TRIAD_BOARD_ROWS = 11
        const val TRIAD_BOARD_COLS = 11
        const val BATTLEFIELD_BOARD_ROWS = 13
        const val BATTLEFIELD_BOARD_COLS = 13

        // DUAL_VALLEY Basis-Positionen
        val BASE_POSITION_P1: Pair<Int, Int> = Pair(2, 2)
        val BASE_POSITION_P2: Pair<Int, Int> = Pair(7, 7)

        // DUAL_VALLEY Startgebiete: Basis + 6 angrenzende Felder
        val START_TERRITORY_P1: List<Pair<Int, Int>> = listOf(
            2 to 2, 1 to 1, 1 to 2, 2 to 1, 2 to 3, 3 to 1, 3 to 2
        )
        val START_TERRITORY_P2: List<Pair<Int, Int>> = listOf(
            7 to 7, 6 to 7, 6 to 8, 7 to 6, 7 to 8, 8 to 7, 8 to 8
        )
    }

    /**
     * Startet das Spiel im passenden Modus: initialisiert Board, Startgebiete und BASE-Units.
     * Dispatcht an den jeweiligen modus-spezifischen Start-Handler.
     *
     * @param state Spielzustand, dessen [at.aau.hexabrawl.websocketserver.model.GameState.gameMode] den Modus bestimmt.
     */
    fun startGame(state: GameState) {
        when (state.gameMode) {
            GameMode.DUAL_VALLEY -> startDualValleyGame(state)
            GameMode.TRIAD_OUTPOST -> startTriadOutpostGame(state)
            GameMode.BATTLEFIELD_PEAKS -> startBattlefieldPeaksGame(state)
        }
    }

    /** Initialisiert ein 2-Spieler-DUAL_VALLEY-Spiel mit 9×9-Board. */
    private fun startDualValleyGame(state: GameState) {
        val p1 = state.players[0]
        val p2 = state.players[1]

        state.units.add(GameUnit(p1.name, BASE_POSITION_P1.first, BASE_POSITION_P1.second, UnitType.BASE))
        state.units.add(GameUnit(p2.name, BASE_POSITION_P2.first, BASE_POSITION_P2.second, UnitType.BASE))

        initializeBoard(state, DUAL_VALLEY_BOARD_COLS, DUAL_VALLEY_BOARD_ROWS,
            mapOf(p1.name to START_TERRITORY_P1, p2.name to START_TERRITORY_P2))

        state.currentTurn = p1.name
        state.status = GameStatus.IN_PROGRESS
        println("Service: GAME STARTED")
    }

    /** Initialisiert ein 3-Spieler-TRIAD_OUTPOST-Spiel mit 11×11-Board. */
    private fun startTriadOutpostGame(state: GameState) {
        val p1 = state.players[0]
        val p2 = state.players[1]
        val p3 = state.players[2]

        val bases = listOf(
            Pair(5, 9),
            Pair(1, 3),
            Pair(9, 3)
        )

        listOf(p1 to bases[0], p2 to bases[1], p3 to bases[2]).forEach { (p, base) ->
            state.units.add(GameUnit(p.name, base.first, base.second, UnitType.BASE))
        }

        val territories = mapOf(
            p1.name to (listOf(bases[0]) + ConnectivityService.hexNeighbors(bases[0].first, bases[0].second)),
            p2.name to (listOf(bases[1]) + ConnectivityService.hexNeighbors(bases[1].first, bases[1].second)),
            p3.name to (listOf(bases[2]) + ConnectivityService.hexNeighbors(bases[2].first, bases[2].second))
        )
        initializeBoard(state, TRIAD_BOARD_COLS, TRIAD_BOARD_ROWS, territories)

        state.currentTurn = p1.name
        state.status = GameStatus.IN_PROGRESS
        println("Service: TRIAD OUTPOST GAME STARTED")
    }

    /** Initialisiert ein 4-Spieler-BATTLEFIELD_PEAKS-Spiel mit 13×13-Board. */
    private fun startBattlefieldPeaksGame(state: GameState) {
        val p1 = state.players[0]
        val p2 = state.players[1]
        val p3 = state.players[2]
        val p4 = state.players[3]

        val bases = listOf(
            Pair(6, 10),
            Pair(2, 6),
            Pair(10, 6),
            Pair(6, 2)
        )

        listOf(p1 to bases[0], p2 to bases[1], p3 to bases[2], p4 to bases[3]).forEach { (p, base) ->
            state.units.add(GameUnit(p.name, base.first, base.second, UnitType.BASE))
        }

        val territories = mapOf(
            p1.name to (listOf(bases[0]) + ConnectivityService.hexNeighbors(bases[0].first, bases[0].second)),
            p2.name to (listOf(bases[1]) + ConnectivityService.hexNeighbors(bases[1].first, bases[1].second)),
            p3.name to (listOf(bases[2]) + ConnectivityService.hexNeighbors(bases[2].first, bases[2].second)),
            p4.name to (listOf(bases[3]) + ConnectivityService.hexNeighbors(bases[3].first, bases[3].second))
        )
        initializeBoard(state, BATTLEFIELD_BOARD_COLS, BATTLEFIELD_BOARD_ROWS, territories)

        state.currentTurn = p1.name
        state.status = GameStatus.IN_PROGRESS
        println("Service: BATTLEFIELD PEAKS GAME STARTED")
    }

    /**
     * Erzeugt alle Felder des Boards ([cols] × [rows]) und weist die in
     * [territories] definierten Startgebiete den jeweiligen Spielern zu.
     *
     * @param state       Spielzustand, dessen fields-Liste befüllt wird.
     * @param cols        Anzahl Spalten des Boards.
     * @param rows        Anzahl Zeilen des Boards.
     * @param territories Map von Spielername zu Liste von (x, y)-Startfeldern.
     */
    private fun initializeBoard(
        state: GameState,
        cols: Int,
        rows: Int,
        territories: Map<String, List<Pair<Int, Int>>>
    ) {
        state.fields.clear()
        for (x in 0 until cols) {
            for (y in 0 until rows) {
                state.fields.add(Field(x, y))
            }
        }
        territories.forEach { (playerName, fields) ->
            fields.forEach { (x, y) ->
                state.fields.firstOrNull { it.x == x && it.y == y }?.owner = playerName
            }
        }
    }

    /**
     * Setzt den [GameState] vollständig zurück: löscht Spieler, Einheiten und Felder
     * und versetzt den Status auf WAITING_FOR_PLAYERS.
     *
     * @param state Spielzustand, der vollständig zurückgesetzt wird.
     * @return Den geleerten [GameState].
     */
    fun initializeGame(state: GameState): GameState = synchronized(state.lock) {
        state.players.clear()
        state.units.clear()
        state.fields.clear()
        state.currentTurn = null
        state.status = GameStatus.WAITING_FOR_PLAYERS
        println("Service: GAME INITIALIZED - Everything cleared")
        return state
    }

    /**
     * Setzt das Spiel auf den Startzustand zurück, behält aber die aktuellen Spieler.
     * Startet das Spiel neu, wenn die nötige Spielerzahl vorhanden ist.
     *
     * @param state Spielzustand, der zurückgesetzt wird.
     * @return Den zurückgesetzten [GameState].
     */
    fun resetToStartCondition(state: GameState): GameState = synchronized(state.lock) {
        state.units.clear()
        state.fields.clear()

        if (state.players.size == state.gameMode.maxPlayers) {
            startGame(state)
        } else {
            val fallbackPos = listOf(
                listOf(1 to 2, 2 to 3, 3 to 2),
                listOf(8 to 7, 7 to 8, 6 to 7)
            )
            state.players.forEachIndexed { index, player ->
                val positions = fallbackPos.getOrElse(index) { emptyList() }
                UnitType.entries
                    .filter { it != UnitType.SKELETON && it != UnitType.BASE }
                    .forEachIndexed { typeIndex, type ->
                        val (x, y) = positions.getOrElse(typeIndex) { index * 4 + typeIndex to 0 }
                        state.units.add(GameUnit(player = player.name, x = x, y = y, type = type))
                    }
            }
            state.currentTurn = state.players.firstOrNull()?.name
            state.status = GameStatus.IN_PROGRESS
        }

        println("Service: Reset to start condition for ${state.players.size} players.")
        return state
    }
}