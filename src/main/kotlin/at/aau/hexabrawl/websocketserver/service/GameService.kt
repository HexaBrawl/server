package at.aau.hexabrawl.websocketserver.service

import at.aau.hexabrawl.websocketserver.model.EconomyService
import at.aau.hexabrawl.websocketserver.model.Field
import at.aau.hexabrawl.websocketserver.model.GameMode
import at.aau.hexabrawl.websocketserver.model.GameState
import at.aau.hexabrawl.websocketserver.model.GameStatus
import at.aau.hexabrawl.websocketserver.model.GameUnit
import at.aau.hexabrawl.websocketserver.model.HexDistance
import at.aau.hexabrawl.websocketserver.model.Move
import at.aau.hexabrawl.websocketserver.model.PendingGift
import at.aau.hexabrawl.websocketserver.model.Player
import at.aau.hexabrawl.websocketserver.model.PlayerColor
import at.aau.hexabrawl.websocketserver.model.UnitType
import org.springframework.stereotype.Service

@Service
class GameService(
    private val combatService: CombatService, private val connectivityService: ConnectivityService, private val economyService: EconomyService
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


        // Board-Dimensionen pro Modus.
        const val DUAL_VALLEY_BOARD_ROWS = 9
        const val DUAL_VALLEY_BOARD_COLS = 9
        const val TRIAD_BOARD_ROWS = 11
        const val TRIAD_BOARD_COLS = 11
        const val BATTLEFIELD_BOARD_ROWS = 13
        const val BATTLEFIELD_BOARD_COLS = 13

        // Bridge zu EconomyService.UNIT_PRICE
        const val UNIT_PRICE = EconomyService.UNIT_PRICE

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
        fun hexNeighbors(x: Int, y: Int): List<Pair<Int, Int>> = ConnectivityService.hexNeighbors(x, y)
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

        // Welche aktiven Spieler haben noch eine Basis?
        val playersWithBase = state.units
            .filter { it.type == UnitType.BASE }
            .map { it.player }
            .toSet()

        // Wer ist in state.players, hat aber keine Basis mehr?
        val playersToEliminate = state.players
            .map { it.name }
            .filter { it !in playersWithBase }

        // Verlierer eliminieren (Felder freigeben, Einheiten löschen)
        playersToEliminate.forEach { eliminatePlayer(state, it) }

        when (state.players.size) {
            0 -> {
                state.status = GameStatus.FINISHED
                state.winner = null
                state.currentTurn = null
            }
            1 -> {
                state.status = GameStatus.FINISHED
                state.winner = state.players.first().name
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

    /**
     * Prueft fuer alle Spieler, ob ihre Felder noch ueber Hex-Nachbarn mit ihrer
     * BASE verbunden sind. Felder ohne Pfad zur BASE werden zu SKELETON-Feldern,
     * Einheiten darauf (ausser BASE/SKELETON) werden zu UnitType.SKELETON.
     *
     * Spieler ohne BASE-Unit werden uebersprungen — checkWinCondition kuemmert
     * sich um deren Ausscheiden.
     */
    fun recomputeConnectivity(state: GameState) = connectivityService.recomputeConnectivity(state)

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
     * Soft-Disconnect: markiert den Spieler als nicht-verbunden und merkt
     * sich den Zeitpunkt. Spieler, Units und Felder bleiben unverändert,
     * sodass ein /reconnect binnen Grace Period nahtlos wieder einsteigen
     * kann. Der eigentliche Hard-Delete passiert via [hardDelete] —
     * aufgerufen vom Scheduled Cleanup (nach Grace) oder vom /leave-Endpoint
     * (sofort).
     */
    fun handleDisconnect(state: GameState, sessionId: String): GameState = synchronized(state.lock) {
        val player = state.players.find { it.sessionId == sessionId } ?: return state
        player.connected = false
        player.disconnectedAt = System.currentTimeMillis()
        println("Service: SOFT DISCONNECT - ${player.name}")
        return state
    }

    /**
     * Hard-Delete eines Spielers (Variante B2).
     *
     * Wird aufgerufen, wenn die Grace Period abgelaufen ist (Scheduled Cleanup)
     * oder ein Spieler explizit /leave drueckt.
     *
     * Verhalten:
     *  - Aktives pendingGift aufraeumen (war frueher in handleDisconnect)
     *  - Felder werden neutral (owner = null), Units komplett geloescht,
     *    currentTurn weitergereicht — alles via [eliminatePlayer]
     *  - checkWinCondition laeuft (1 Player mit BASE uebrig → Win)
     *  - KEIN FINISHED-Fallback fuer TRIAD/BATTLEFIELD (wurde schon entfernt)
     */
    internal fun hardDelete(state: GameState, player: Player): Unit = synchronized(state.lock) {
        // Aktives Cheat-Geschenk aufraeumen, falls der disconnectete Spieler beteiligt war
        state.pendingGift?.let { gift ->
            if (gift.ownerName == player.name) {
                state.pendingGift = null
            } else {
                val remaining = gift.pendingDecisions - 1
                state.pendingGift = if (remaining > 0) {
                    gift.copy(pendingDecisions = remaining)
                } else {
                    null
                }
            }
        }

        println("Service: HARD DELETE - ${player.name}")

        eliminatePlayer(state, player.name)
        checkWinCondition(state)

        if (state.status == GameStatus.FINISHED) {
            println("Service: GAME FINISHED - winner: ${state.winner}")
        }
    }

    fun handleDisconnect(sessionId: String): GameState = handleDisconnect(this.gameState, sessionId)

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

    /**
     * Entfernt einen Spieler restlos aus dem Spiel (bei Disconnect oder Basis-Verlust).
     * Räumt Felder auf, löscht Einheiten und übergibt den Zug, falls nötig.
     */
    private fun eliminatePlayer(state: GameState, playerName: String) {
        // 1. Zug sauber weitergeben, falls der ausscheidende Spieler gerade am Zug ist.
        // WICHTIG: Das muss passieren, BEVOR der Spieler aus state.players gelöscht wird!
        if (state.currentTurn == playerName) {
            switchTurn(state)
        }

        // 2. Spieler aus der Liste entfernen
        state.players.removeIf { it.name == playerName }

        // 3. Alle Einheiten des Spielers entfernen
        state.units.removeIf { it.player == playerName }

        // 4. Felder des Spielers neutralisieren (gehören niemandem mehr)
        state.fields.filter { it.owner == playerName }.forEach { field ->
            field.owner = null
            field.isSkeleton = false
        }
    }
}