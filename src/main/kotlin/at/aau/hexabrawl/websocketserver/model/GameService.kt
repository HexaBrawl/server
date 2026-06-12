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
        const val FIELD_INCOME_PER_ROUND = 1
        const val FARM_BASE_COST = 10
        const val FARM_COST_INCREMENT = 1

        // Board-Dimensionen pro Modus.
        const val DUAL_VALLEY_BOARD_ROWS = 9
        const val DUAL_VALLEY_BOARD_COLS = 9
        const val TRIAD_BOARD_ROWS = 11
        const val TRIAD_BOARD_COLS = 11
        const val BATTLEFIELD_BOARD_ROWS = 13
        const val BATTLEFIELD_BOARD_COLS = 13

        // Preis fuer eine gekaufte Einheit (#132).
        // Synchron mit BottomHudLogic.priceOf in der App halten.
        const val UNIT_PRICE = 5

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

        // Nur Basen platzieren -- Kampfeinheiten kaufen die Spieler ueber
        // den Buy-Unit-Endpoint, sobald sie genug Gold haben.
        state.units.add(GameUnit(p1.name, BASE_POSITION_P1.first, BASE_POSITION_P1.second, UnitType.BASE))
        state.units.add(GameUnit(p2.name, BASE_POSITION_P2.first, BASE_POSITION_P2.second, UnitType.BASE))

        initializeBoard(state, DUAL_VALLEY_BOARD_COLS, DUAL_VALLEY_BOARD_ROWS,
            mapOf(p1.name to START_TERRITORY_P1, p2.name to START_TERRITORY_P2))

        state.currentTurn = p1.name
        state.status = GameStatus.IN_PROGRESS
        println("Service: GAME STARTED")
    }

    private fun startTriadOutpostGame(state: GameState) {
        val p1 = state.players[0]
        val p2 = state.players[1]
        val p3 = state.players[2]

        // Basen: gleichmaessiges Dreieck auf 12x12, je 8 Hex-Schritte voneinander entfernt.
        val bases = listOf(
            Pair(5, 9),  // P1 Sueden
            Pair(1, 3),  // P2 Nordwesten
            Pair(9, 3)   // P3 Nordosten
        )

        // Nur Basen platzieren -- Kampfeinheiten werden ueber Buy-Unit gekauft.
        listOf(p1 to bases[0], p2 to bases[1], p3 to bases[2]).forEach { (p, base) ->
            state.units.add(GameUnit(p.name, base.first, base.second, UnitType.BASE))
        }

        val territories = mapOf(
            p1.name to (listOf(bases[0]) + hexNeighbors(bases[0].first, bases[0].second)),
            p2.name to (listOf(bases[1]) + hexNeighbors(bases[1].first, bases[1].second)),
            p3.name to (listOf(bases[2]) + hexNeighbors(bases[2].first, bases[2].second))
        )
        initializeBoard(state, TRIAD_BOARD_COLS, TRIAD_BOARD_ROWS, territories)

        state.currentTurn = p1.name
        state.status = GameStatus.IN_PROGRESS
        println("Service: TRIAD OUTPOST GAME STARTED")
    }

    private fun startBattlefieldPeaksGame(state: GameState) {
        val p1 = state.players[0]
        val p2 = state.players[1]
        val p3 = state.players[2]
        val p4 = state.players[3]

        // Basen: Kreuzformation auf 13x13, 6 Schritte zu Nachbarn, 8 zu Gegenueber.
        val bases = listOf(
            Pair(6, 10),  // P1 Sued
            Pair(2,  6),  // P2 West
            Pair(10, 6),  // P3 Ost
            Pair(6,  2)   // P4 Nord
        )

        // Nur Basen platzieren -- Kampfeinheiten werden ueber Buy-Unit gekauft.
        listOf(p1 to bases[0], p2 to bases[1], p3 to bases[2], p4 to bases[3]).forEach { (p, base) ->
            state.units.add(GameUnit(p.name, base.first, base.second, UnitType.BASE))
        }

        val territories = mapOf(
            p1.name to (listOf(bases[0]) + hexNeighbors(bases[0].first, bases[0].second)),
            p2.name to (listOf(bases[1]) + hexNeighbors(bases[1].first, bases[1].second)),
            p3.name to (listOf(bases[2]) + hexNeighbors(bases[2].first, bases[2].second)),
            p4.name to (listOf(bases[3]) + hexNeighbors(bases[3].first, bases[3].second))
        )
        initializeBoard(state, BATTLEFIELD_BOARD_COLS, BATTLEFIELD_BOARD_ROWS, territories)

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

    private fun checkWinCondition(state: GameState) {
        if (state.status != GameStatus.IN_PROGRESS) return

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

        // Wirtschaft des Spielers, dessen Zug gerade endet
        state.players.firstOrNull { it.name == state.currentTurn }?.let {
            applyEconomy(state, it)
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

    /**
     * Prueft fuer alle Spieler, ob ihre Felder noch ueber Hex-Nachbarn mit ihrer
     * BASE verbunden sind. Felder ohne Pfad zur BASE werden zu SKELETON-Feldern,
     * Einheiten darauf (ausser BASE/SKELETON) werden zu UnitType.SKELETON.
     *
     * Spieler ohne BASE-Unit werden uebersprungen — checkWinCondition kuemmert
     * sich um deren Ausscheiden.
     */
    fun recomputeConnectivity(state: GameState) {
        state.players.forEach { player ->
            val baseUnit = state.units.firstOrNull {
                it.player == player.name && it.type == UnitType.BASE
            } ?: return@forEach

            val connected = bfsConnectedFields(state, player.name, baseUnit.x, baseUnit.y)

            state.fields.filter { it.owner == player.name && !it.isSkeleton }.forEach { field ->
                if ((field.x to field.y) !in connected) {
                    field.isSkeleton = true
                    state.units.filter {
                        it.x == field.x && it.y == field.y &&
                            it.player == player.name &&
                            it.type != UnitType.BASE &&
                            it.type != UnitType.SKELETON
                    }.forEach { it.type = UnitType.SKELETON }
                }
            }
        }
    }

    private fun bfsConnectedFields(
        state: GameState,
        playerName: String,
        startX: Int,
        startY: Int
    ): Set<Pair<Int, Int>> {
        val visited = mutableSetOf(startX to startY)
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(startX to startY)

        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeFirst()
            for ((nx, ny) in hexNeighbors(x, y)) {
                if ((nx to ny) in visited) continue
                val field = state.fields.firstOrNull { it.x == nx && it.y == ny } ?: continue
                if (field.owner != playerName) continue
                if (field.isSkeleton) continue
                visited.add(nx to ny)
                queue.add(nx to ny)
            }
        }
        return visited
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
     *
     * Delegiert an startGame, wenn alle Spieler fuer den aktuellen Modus
     * vorhanden sind. Andernfalls werden nur DUAL_VALLEY-Einheiten als
     * Fallback gesetzt (Testendpunkt mit unvollstaendiger Spielerzahl).
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
    private fun applyEconomy(state: GameState, player: Player) {
        player.gold += computeIncome(player, state)
        val upkeep = computeUpkeep(player, state)
        val playerUnits = state.units.filter {
            it.player == player.name && it.type != UnitType.SKELETON && it.type != UnitType.BASE
        }

        if (player.gold >= upkeep) {
            player.gold -= upkeep
        } else {
            player.gold = 0
            playerUnits.forEach { it.type = UnitType.SKELETON }
        }
    }

    /**
     * Kauft eine neue Einheit und platziert sie an (x, y) (#132).
     *
     * Diese Methode setzt voraus, dass der Controller bereits alle
     * Validierungen durchgefuehrt hat (Spieler am Zug, Type gueltig,
     * Feld gehoert dem Spieler, Feld nicht besetzt, genug Gold).
     *
     * Verhalten:
     *  - Falls ein Skelett auf dem Zielfeld steht, wird es entfernt
     *    (analog zum Move-Verhalten aus #104). Konsistent mit der
     *    Annahme, dass ein eigenes Feld zum eigenen Territorium gehoert
     *    und damit mit der Basis verbunden ist.
     *  - Gold wird abgezogen.
     *  - Neue Einheit wird mit hasMovedThisTurn = true hinzugefuegt —
     *    eine im selben Zug gekaufte Einheit darf nicht zusaetzlich
     *    noch ziehen.
     *
     * Thread-safe ueber state.lock.
     */
    fun buyUnit(
        state: GameState,
        playerName: String,
        type: UnitType,
        x: Int,
        y: Int
    ): GameState = synchronized(state.lock) {
        val player = state.players.find { it.name == playerName } ?: return state

        // Skelett auf dem Zielfeld entfernen, falls vorhanden
        state.units.removeIf {
            it.x == x && it.y == y && it.type == UnitType.SKELETON
        }

        // Gold abziehen
        player.gold -= UNIT_PRICE

        // Neue Einheit mit hasMovedThisTurn = true
        state.units.add(
            GameUnit(
                player = playerName,
                x = x,
                y = y,
                type = type,
                hasMovedThisTurn = true
            )
        )

        return state
    }
    private fun computeIncome(player: Player, state: GameState): Int {
        val ownedFields = state.fields.count { it.owner == player.name && !it.isSkeleton }
        return ownedFields * FIELD_INCOME_PER_ROUND + player.farms * FARM_INCOME_PER_ROUND
    }

    private fun computeUpkeep(player: Player, state: GameState): Int {
        val unitCount = state.units.count {
            it.player == player.name && it.type != UnitType.SKELETON && it.type != UnitType.BASE
        }
        return (0 until unitCount).sumOf { 3 + it }
    }

    fun recomputePlayerStats(state: GameState) {
        state.players.forEach { player ->
            player.income = computeIncome(player, state)
            player.upkeep = computeUpkeep(player, state)
        }
    }

    /**
     * Schummel-Geschenk oeffnen (Cheat-Gift).
     *
     * Diese Methode setzt voraus, dass der Controller bereits alle
     * Validierungen durchgefuehrt hat (Status IN_PROGRESS, delta in
     * -10..10, pendingGift == null, Spieler existiert, hasUsedGift
     * == false).
     *
     * Verhalten:
     *  - delta wird auf player.gold gebucht. Bei Resultat < 0 wird
     *    auf 0 gecappt (User-Entscheidung: kein negatives Gold).
     *  - player.hasUsedGift wird auf true gesetzt (sticky bis Spielende).
     *  - state.pendingGift wird mit pendingDecisions = players.size - 1
     *    angelegt, sodass alle anderen Spieler die Steal-Entscheidung
     *    treffen koennen.
     *
     * Thread-safe ueber state.lock.
     */
    fun claimCheatGift(
        state: GameState,
        playerName: String,
        delta: Int
    ): GameState = synchronized(state.lock) {
        val player = state.players.find { it.name == playerName } ?: return state

        player.gold += delta
        if (player.gold < 0) player.gold = 0
        player.hasUsedGift = true

        state.pendingGift = PendingGift(
            ownerName = player.name,
            delta = delta,
            pendingDecisions = state.players.size - 1
        )
        return state
    }

    /**
     * Antwort auf das Schummel-Geschenk (Steal-Versuch).
     *
     * Diese Methode setzt voraus, dass der Controller bereits validiert
     * hat (pendingGift != null, playerName != ownerName, Spieler existiert).
     *
     * Bei accept=true (atomarer Steal-Switch):
     *  - owner.gold -= delta, bei Resultat < 0 wird auf 0 gecappt
     *  - stealer.gold += delta, bei Resultat < 0 wird auf 0 gecappt
     *  - pendingGift = null
     *
     * Bei accept=false:
     *  - pendingDecisions -= 1
     *  - bei 0 → pendingGift = null (keiner wollte stehlen)
     *
     * Thread-safe ueber state.lock.
     */
    fun respondCheatSteal(
        state: GameState,
        playerName: String,
        accept: Boolean
    ): GameState = synchronized(state.lock) {
        val gift = state.pendingGift ?: return state
        val player = state.players.find { it.name == playerName } ?: return state

        if (accept) {
            val owner = state.players.first { it.name == gift.ownerName }
            owner.gold -= gift.delta
            if (owner.gold < 0) owner.gold = 0
            player.gold += gift.delta
            if (player.gold < 0) player.gold = 0
            state.pendingGift = null
        } else {
            val remaining = gift.pendingDecisions - 1
            state.pendingGift = if (remaining > 0) {
                gift.copy(pendingDecisions = remaining)
            } else {
                null
            }
        }
        return state
    }
}
