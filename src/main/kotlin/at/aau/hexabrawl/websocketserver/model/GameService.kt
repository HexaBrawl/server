package at.aau.hexabrawl.websocketserver.model

import org.springframework.stereotype.Service

@Service
class GameService(
    private val combatService: CombatService
) {

    val gameState = GameState()

    companion object {
        // veraltet — Mode-spezifische Spielerzahl kommt aus state.gameMode.maxPlayers (#51).
        // Wird noch von einigen Tests referenziert, daher nicht entfernt.
        const val MAX_PLAYERS = 2

        // Maximale Hex-Distanz, die eine Einheit pro Zug zuruecklegen darf.
        const val MAX_MOVE_DISTANCE = 2

        // Wirtschaftssystem (#60)
        const val STARTING_GOLD = 6
        const val FARM_INCOME_PER_ROUND = 3
        const val FARM_BASE_COST = 10
        const val FARM_COST_INCREMENT = 1

        // Board-Dimensionen pro Modus.
        const val DUAL_VALLEY_BOARD_ROWS = 10
        const val DUAL_VALLEY_BOARD_COLS = 10
        const val TRIAD_BOARD_ROWS = 12
        const val TRIAD_BOARD_COLS = 12
        const val BATTLEFIELD_BOARD_ROWS = 13
        const val BATTLEFIELD_BOARD_COLS = 13

        // Basis-Positionen fuer DUAL_VALLEY (#104).
        val BASE_POSITION_P1: Pair<Int, Int> = Pair(2, 2)
        val BASE_POSITION_P2: Pair<Int, Int> = Pair(7, 7)

        // Startgebiete DUAL_VALLEY: Basis + 6 angrenzende Felder.
        val START_TERRITORY_P1: List<Pair<Int, Int>> = listOf(
            2 to 2, 1 to 1, 1 to 2, 2 to 1, 2 to 3, 3 to 1, 3 to 2
        )
        val START_TERRITORY_P2: List<Pair<Int, Int>> = listOf(
            7 to 7, 6 to 7, 6 to 8, 7 to 6, 7 to 8, 8 to 7, 8 to 8
        )

        // Liefert die 6 Nachbarfelder eines Hex-Feldes in "odd-q offset" Koordinaten.
        fun hexNeighbors(x: Int, y: Int): List<Pair<Int, Int>> =
            if (x % 2 == 0)
                listOf(x - 1 to y - 1, x - 1 to y, x to y - 1, x to y + 1, x + 1 to y - 1, x + 1 to y)
            else
                listOf(x - 1 to y, x - 1 to y + 1, x to y - 1, x to y + 1, x + 1 to y, x + 1 to y + 1)
    }

    /**
     * Fuegt einen Spieler dem Spiel hinzu.
     *
     * Konsolidiert drei Issue-Stroeme:
     *  - Multi-Mode (#51): Maximale Spielerzahl kommt aus state.gameMode.maxPlayers
     *  - Color-Auswahl (#107): Farbe kann vom Client mitgegeben werden,
     *    Fallback ist dynamisch je nach Position in der Spielerliste
     *  - Wirtschaft (#60): Neue Spieler bekommen STARTING_GOLD
     *
     * Thread-safe ueber state.lock.
     */
    fun handleJoin(
        state: GameState,
        playerName: String,
        sessionId: String = "",
        color: PlayerColor? = null
    ): GameState = synchronized(state.lock) {
        if (!state.players.any { it.name == playerName } && state.players.size < state.gameMode.maxPlayers) {

            val colors = listOf(PlayerColor.RED, PlayerColor.BLUE, PlayerColor.GREEN, PlayerColor.YELLOW)
            val assignedColor = color ?: colors.getOrElse(state.players.size) { PlayerColor.RED }

            if (state.players.any { it.color == assignedColor }) {
                return@synchronized state
            }

            state.players.add(Player(playerName, sessionId, assignedColor, STARTING_GOLD))
            println("JOIN: $playerName (color: $assignedColor)")
        }

        // Auto-Start bei Erreichen der Mode-Spielerzahl
        if (state.players.size == state.gameMode.maxPlayers && state.units.isEmpty()) {
            println("players=${state.players.size}, max=${state.gameMode.maxPlayers}")
            startGame(state)
        }
        return state
    }

    // Bridge fuer den globalen Single-Game-Pfad (Tests + Backward-Compat).
    fun handleJoin(
        playerName: String,
        sessionId: String = "",
        color: PlayerColor? = null
    ): GameState = handleJoin(this.gameState, playerName, sessionId, color)

    fun isColorTaken(state: GameState, color: PlayerColor): Boolean = synchronized(state.lock) {
        return state.players.any { it.color == color }
    }

    fun isColorTaken(color: PlayerColor): Boolean = isColorTaken(this.gameState, color)

    /**
     * Dispatcht den modus-spezifischen Spielstart.
     */
    fun startGame(state: GameState) {
        when (state.gameMode) {
            GameMode.DUAL_VALLEY -> startDualValleyGame(state)
            GameMode.TRIAD_OUTPOST -> startTriadOutpostGame(state)
            GameMode.BATTLEFIELD_PEAKS -> startBattlefieldPeaksGame(state)
        }
    }

    private fun startDualValleyGame(state: GameState) {
        val p1 = state.players[0]
        val p2 = state.players[1]

        val startPositionsP1 = listOf(
            Pair(1, 2),  // ARCHER  (linker Nachbar der Basis)
            Pair(2, 3),  // INFANTRY (unterer Nachbar)
            Pair(3, 2)   // CAVALRY  (rechter Nachbar)
        )

        val startPositionsP2 = listOf(
            Pair(8, 7),  // ARCHER
            Pair(7, 8),  // INFANTRY
            Pair(6, 7)   // CAVALRY
        )

        UnitType.entries
            .filter { it != UnitType.SKELETON && it != UnitType.BASE }
            .forEachIndexed { index, type ->
                val (x1, y1) = startPositionsP1[index]
                val (x2, y2) = startPositionsP2[index]
                state.units.add(GameUnit(p1.name, x1, y1, type))
                state.units.add(GameUnit(p2.name, x2, y2, type))
            }

        state.units.add(GameUnit(p1.name, BASE_POSITION_P1.first, BASE_POSITION_P1.second, UnitType.BASE))
        state.units.add(GameUnit(p2.name, BASE_POSITION_P2.first, BASE_POSITION_P2.second, UnitType.BASE))

        initializeBoard(state, DUAL_VALLEY_BOARD_COLS, DUAL_VALLEY_BOARD_ROWS,
            mapOf(p1.name to START_TERRITORY_P1, p2.name to START_TERRITORY_P2))

        state.currentTurn = p1.name
        state.status = GameStatus.IN_PROGRESS
        println("Service: GAME STARTED")
    }

    /**
     * Startet ein TRIAD_OUTPOST-Spiel (3 Spieler).
     *
     * TODO: BASE-Positionen fuer 3 Spieler designen. Bis dahin ist
     * [checkWinCondition] in diesem Modus deaktiviert.
     */
    private fun startTriadOutpostGame(state: GameState) {
        val p1 = state.players[0]
        val p2 = state.players[1]
        val p3 = state.players[2]

        val startPositionsP1 = listOf(
            Pair(4, 2),  // ARCHER (Sueden)
            Pair(5, 2),  // INFANTRY
            Pair(6, 2)   // CAVALRY
        )

        val startPositionsP2 = listOf(
            Pair(2, 5),  // ARCHER (Nordwesten)
            Pair(2, 6),  // INFANTRY
            Pair(3, 6)   // CAVALRY
        )

        val startPositionsP3 = listOf(
            Pair(7, 6),  // ARCHER (Nordosten)
            Pair(8, 6),  // INFANTRY
            Pair(8, 5)   // CAVALRY
        )

        UnitType.entries
            .filter { it != UnitType.SKELETON && it != UnitType.BASE }
            .forEachIndexed { index, type ->
                val (x1, y1) = startPositionsP1[index]
                val (x2, y2) = startPositionsP2[index]
                val (x3, y3) = startPositionsP3[index]
                state.units.add(GameUnit(p1.name, x1, y1, type))
                state.units.add(GameUnit(p2.name, x2, y2, type))
                state.units.add(GameUnit(p3.name, x3, y3, type))
            }

        state.currentTurn = p1.name
        state.status = GameStatus.IN_PROGRESS
        println("Service: TRIAD OUTPOST GAME STARTED")
    }

    /**
     * Startet ein BATTLEFIELD_PEAKS-Spiel (4 Spieler).
     *
     * TODO: BASE-Positionen fuer 4 Spieler designen. Bis dahin ist
     * [checkWinCondition] in diesem Modus deaktiviert.
     */
    private fun startBattlefieldPeaksGame(state: GameState) {
        val p1 = state.players[0]
        val p2 = state.players[1]
        val p3 = state.players[2]
        val p4 = state.players[3]

        val startPositionsP1 = listOf(
            Pair(4, 1),  // ARCHER (Sueden)
            Pair(5, 1),  // INFANTRY
            Pair(6, 1)   // CAVALRY
        )

        val startPositionsP2 = listOf(
            Pair(1, 4),  // ARCHER (Westen)
            Pair(1, 5),  // INFANTRY
            Pair(1, 6)   // CAVALRY
        )

        val startPositionsP3 = listOf(
            Pair(9, 4),  // ARCHER (Osten)
            Pair(9, 5),  // INFANTRY
            Pair(9, 6)   // CAVALRY
        )

        val startPositionsP4 = listOf(
            Pair(4, 9),  // ARCHER (Norden)
            Pair(5, 9),  // INFANTRY
            Pair(6, 9)   // CAVALRY
        )

        UnitType.entries
            .filter { it != UnitType.SKELETON && it != UnitType.BASE }
            .forEachIndexed { index, type ->
                val (x1, y1) = startPositionsP1[index]
                val (x2, y2) = startPositionsP2[index]
                val (x3, y3) = startPositionsP3[index]
                val (x4, y4) = startPositionsP4[index]
                state.units.add(GameUnit(p1.name, x1, y1, type))
                state.units.add(GameUnit(p2.name, x2, y2, type))
                state.units.add(GameUnit(p3.name, x3, y3, type))
                state.units.add(GameUnit(p4.name, x4, y4, type))
            }

        state.currentTurn = p1.name
        state.status = GameStatus.IN_PROGRESS
        println("Service: BATTLEFIELD PEAKS GAME STARTED")
    }

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

        // Randfeld-Regel: nur fuer DUAL_VALLEY, wo das Board initialisiert ist.
        if (state.gameMode == GameMode.DUAL_VALLEY) {
            val targetField = state.fields.firstOrNull { it.x == move.toX && it.y == move.toY }
            val isOwnField = targetField?.owner == move.player
            val isBorderField = isAdjacentToOwnTerritory(state, move.toX, move.toY, move.player)
            if (!isOwnField && !isBorderField) return state
        }

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

    /**
     * Win-Condition basiert auf BASE-Existenz und ist aktuell
     * nur fuer [GameMode.DUAL_VALLEY] aktiv.
     */
    private fun checkWinCondition(state: GameState) {
        if (state.status != GameStatus.IN_PROGRESS) return
        if (state.gameMode != GameMode.DUAL_VALLEY) return

        val playersWithBase = state.units
            .filter { it.type == UnitType.BASE }
            .map { it.player }
            .distinct()

        when (playersWithBase.size) {
            0 -> {
                state.status = GameStatus.FINISHED
                state.winner = null
                state.currentTurn = null
            }
            1 -> {
                state.status = GameStatus.FINISHED
                state.winner = playersWithBase[0]
                state.currentTurn = null
            }
            else -> {
                // >= 2: Spiel laeuft weiter.
            }
        }
    }

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
     * Erzeugt alle Felder des Boards und weist die Startgebiete zu.
     *
     * @param cols  Anzahl der Spalten
     * @param rows  Anzahl der Zeilen
     * @param territories  Mapping von Spielername → Liste der Startfelder
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
     * Naechsten Spieler an die Reihe nehmen.
     *
     *  - 2 Spieler: einfache Alternation
     *  - 3/4 Spieler: zyklische Rotation (#51)
     *  - hasMovedThisTurn wird immer zurueckgesetzt (#61)
     *  - Runden-Wrap-Around: Upkeep sobald wieder Spieler 1 dran ist (#60)
     */
    private fun switchTurn(state: GameState) {
        if (state.players.isEmpty()) return

        val wasFirstBefore = state.currentTurn == state.players[0].name

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

        // Neue Runde: alle Einheiten duerfen wieder ziehen.
        state.units.forEach { it.hasMovedThisTurn = false }

        // Runden-Wrap-Around: Upkeep wenn wieder Spieler 1 dran ist.
        if (!wasFirstBefore && state.currentTurn == state.players[0].name) {
            applyUpkeep(state)
        }
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
     */
    private fun finishMove(state: GameState, unit: GameUnit, playerName: String) {
        if (unit in state.units) {
            unit.hasMovedThisTurn = true
            // Felderoberung nur fuer DUAL_VALLEY.
            if (state.gameMode == GameMode.DUAL_VALLEY) {
                state.fields.firstOrNull { it.x == unit.x && it.y == unit.y }
                    ?.let { it.owner = playerName }
            }
        }
        // DUAL_VALLEY: Turn erst wechseln wenn alle Einheiten gezogen haben.
        // TRIAD/BATTLEFIELD: sofort nach jedem Zug wechseln.
        if (state.gameMode == GameMode.DUAL_VALLEY) {
            if (allMovableUnitsHaveMoved(state, playerName)) {
                switchTurn(state)
            }
        } else {
            switchTurn(state)
        }
    }

    private fun isAdjacentToOwnTerritory(state: GameState, x: Int, y: Int, playerName: String): Boolean {
        return state.fields.any { field ->
            field.owner == playerName &&
                    HexDistance.between(field.x, field.y, x, y) == 1
        }
    }

    // WICHTIG FÜR TEST — nur den aktuellen Stand lesen
    fun getCurrentState(state: GameState): GameState = synchronized(state.lock) {
        return state
    }

    fun getCurrentState(): GameState = getCurrentState(this.gameState)

    // ALLES AUF NULL — fuer /test/init
    fun initializeGame(state: GameState): GameState = synchronized(state.lock) {
        state.players.clear()
        state.units.clear()
        state.fields.clear()
        state.currentTurn = null
        state.status = GameStatus.WAITING_FOR_PLAYERS
        println("Service: GAME INITIALIZED - Everything cleared")
        return state
    }

    fun initializeGame(): GameState = initializeGame(this.gameState)

    /**
     * SPIELER BEHALTEN — fuer /test/reset.
     */
    fun resetToStartCondition(state: GameState): GameState = synchronized(state.lock) {
        state.units.clear()

        val dualValleyUnitPos = listOf(
            listOf(1 to 2, 2 to 3, 3 to 2),  // P1
            listOf(8 to 7, 7 to 8, 6 to 7)    // P2
        )

        state.players.forEachIndexed { index, player ->
            val positions = dualValleyUnitPos.getOrElse(index) { emptyList() }

            UnitType.entries
                .filter { it != UnitType.SKELETON && it != UnitType.BASE }
                .forEachIndexed { typeIndex, type ->
                    val (x, y) = positions.getOrElse(typeIndex) { index * 4 + typeIndex to 0 }
                    state.units.add(GameUnit(player = player.name, x = x, y = y, type = type))
                }

            // BASE nur fuer DUAL_VALLEY mit 2 Spielern
            if (state.gameMode == GameMode.DUAL_VALLEY && state.players.size == 2) {
                val basePos = if (index == 0) BASE_POSITION_P1 else BASE_POSITION_P2
                state.units.add(GameUnit(player.name, basePos.first, basePos.second, UnitType.BASE))
            }
        }

        if (state.gameMode == GameMode.DUAL_VALLEY && state.players.size == 2) {
            initializeBoard(state, DUAL_VALLEY_BOARD_COLS, DUAL_VALLEY_BOARD_ROWS,
                mapOf(state.players[0].name to START_TERRITORY_P1, state.players[1].name to START_TERRITORY_P2))
        } else {
            state.fields.clear()
        }

        state.currentTurn = state.players.firstOrNull()?.name
        state.status = GameStatus.IN_PROGRESS

        println("Service: Reset - Units for ${state.players} recreated at start positions.")
        return state
    }

    fun resetToStartCondition(): GameState = resetToStartCondition(this.gameState)

    /**
     * Entfernt einen disconnecteten Spieler und seine Einheiten.
     *
     *  - DUAL_VALLEY: checkWinCondition (BASE-basiert)
     *  - TRIAD/BATTLEFIELD: Fallback — Disconnect beendet das Spiel sofort
     */
    fun handleDisconnect(state: GameState, sessionId: String): GameState = synchronized(state.lock) {
        val player = state.players.find { it.sessionId == sessionId }
            ?: return state

        state.players.remove(player)
        state.units.removeIf { it.player == player.name }

        checkWinCondition(state)

        // Fallback fuer Modi ohne BASE-Win
        if (state.status == GameStatus.IN_PROGRESS && state.gameMode != GameMode.DUAL_VALLEY) {
            state.status = GameStatus.FINISHED
            state.currentTurn = null
        }

        if (state.status == GameStatus.FINISHED) {
            println("Service: GAME FINISHED - ${player.name} disconnected, winner: ${state.winner}")
        } else {
            println("Service: PLAYER LEFT - ${player.name} disconnected")
        }

        return state
    }

    fun handleDisconnect(sessionId: String): GameState = handleDisconnect(this.gameState, sessionId)

    /**
     * Wirtschafts-Rundenabschluss (#60).
     * Farm-Einkommen gutschreiben, Unterhalt abziehen (1. Einheit 3 Gold, jede weitere +1).
     * Bei Insolvenz: Gold auf 0, alle lebenden Truppen → SKELETON.
     */
    private fun applyUpkeep(state: GameState) {
        state.players.forEach { player ->
            player.gold += player.farms * FARM_INCOME_PER_ROUND

            val playerUnits = state.units.filter {
                it.player == player.name && it.type != UnitType.SKELETON && it.type != UnitType.BASE
            }
            val unitCount = playerUnits.size
            val upkeep = (0 until unitCount).sumOf { 3 + it }

            if (player.gold >= upkeep) {
                player.gold -= upkeep
            } else {
                player.gold = 0
                playerUnits.forEach { it.type = UnitType.SKELETON }
            }
        }

        checkWinCondition(state)
    }

    /**
     * Kauft eine Farm fuer den Spieler (#60).
     * Preis: FARM_BASE_COST + farms * FARM_COST_INCREMENT (10, 11, 12, ...).
     */
    fun buyFarm(state: GameState, playerName: String): GameState = synchronized(state.lock) {
        if (state.status != GameStatus.IN_PROGRESS) return state

        val player = state.players.find { it.name == playerName } ?: return state

        val cost = FARM_BASE_COST + (player.farms * FARM_COST_INCREMENT)

        if (player.gold >= cost) {
            player.gold -= cost
            player.farms += 1
            println("Service: $playerName kaufte Farm für $cost. (Total: ${player.farms})")
        } else {
            println("Service: $playerName hat zu wenig Gold ($cost) für Farm.")
        }
        return state
    }
}
