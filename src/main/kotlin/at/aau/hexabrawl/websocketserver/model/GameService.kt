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

        // Wirtschaftssystem (#60)
        const val STARTING_GOLD = 6
        const val FARM_INCOME_PER_ROUND = 3
        const val FARM_BASE_COST = 10
        const val FARM_COST_INCREMENT = 1

        // Basis-Positionen für DUAL_VALLEY (#104). Sieg-relevant — siehe checkWinCondition.
        // Bewusst an die Grid-Raender gesetzt, damit haeufige Test-Move-Ziele
        // wie (6,6) oder (3,1) frei bleiben.
        val BASE_POSITION_P1: Pair<Int, Int> = Pair(3, 0)
        val BASE_POSITION_P2: Pair<Int, Int> = Pair(6, 7)
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
     * Farb-Konflikt wird hier defensiv abgelehnt (Controller schickt
     * COLOR_ALREADY_TAKEN). Sobald die Mode-Anzahl erreicht ist, startet
     * das Spiel ueber [startGame].
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

            // Farbe: explizit vom Client (#107) oder dynamisch via Position (#51)
            val colors = listOf(PlayerColor.RED, PlayerColor.BLUE, PlayerColor.GREEN, PlayerColor.YELLOW)
            val assignedColor = color ?: colors.getOrElse(state.players.size) { PlayerColor.RED }

            // Farb-Konflikt: defensiv ablehnen (Controller meldet COLOR_ALREADY_TAKEN)
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

    /**
     * Prueft ob eine Farbe bereits vergeben ist. Wird vom Controller
     * fuer COLOR_ALREADY_TAKEN-Errors genutzt.
     */
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

    /**
     * Startet ein DUAL_VALLEY-Spiel.
     *
     * Setzt die drei Kampfeinheiten (ARCHER/INFANTRY/CAVALRY) auf den
     * vordefinierten Positionen und platziert pro Spieler eine BASE
     * an [BASE_POSITION_P1]/[BASE_POSITION_P2]. Die BASE ist die
     * sieg-relevante Entitaet — siehe [checkWinCondition].
     */
    private fun startDualValleyGame(state: GameState) {
        val p1 = state.players[0]
        val p2 = state.players[1]

        val startPositionsP1 = listOf(
            Pair(2, 2),  // ARCHER
            Pair(3, 2),  // INFANTRY
            Pair(4, 2)   // CAVALRY
        )

        val startPositionsP2 = listOf(
            Pair(5, 5),
            Pair(6, 5),
            Pair(7, 5)
        )

        // BASE und SKELETON gehoeren nicht in den Start-Loop:
        // - SKELETON entsteht nur durch Tode/Insolvenz
        // - BASE wird unten separat platziert (vermeidet IndexOutOfBounds,
        //   weil startPositions nur 3 Eintraege hat)
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

        state.currentTurn = p1.name
        state.status = GameStatus.IN_PROGRESS
        println("Service: GAME STARTED")
    }

    /**
     * Startet ein TRIAD_OUTPOST-Spiel (3 Spieler).
     *
     * TODO: BASE-Positionen fuer 3 Spieler designen. Bis dahin ist
     * [checkWinCondition] in diesem Modus deaktiviert (Spielende
     * passiert nur ueber Disconnect).
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
     * [checkWinCondition] in diesem Modus deaktiviert (Spielende
     * passiert nur ueber Disconnect).
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

    /**
     * Fuehrt einen Move aus.
     *
     * Konsolidiert die Multi-Mode-Logik (#51, Turn-Rotation) mit dem
     * BASE-Handling (#104, main):
     *  - Basen sind stationaer (Move auf type=BASE wird abgelehnt)
     *  - Eigene Einheit auf Zielfeld: Move abgelehnt
     *  - Gegnerische BASE auf Zielfeld: instant-destroy, Sieg via
     *    [checkWinCondition]
     *  - Gegnerische Einheit auf Zielfeld: normales Combat
     *  - Sonst: einfacher Move
     *
     * Nach jeder erfolgreichen Aktion: switchTurn + checkWinCondition.
     */
    fun handleMove(state: GameState, move: Move): GameState = synchronized(state.lock) {
        if (state.status != GameStatus.IN_PROGRESS) return state
        if (move.player != state.currentTurn) return state

        // Basen sind stationaer
        if (move.type == UnitType.BASE) return state

        val unit = state.units.firstOrNull {
            it.player == move.player &&
                    it.type == move.type &&
                    it.type != UnitType.SKELETON &&
                    it.x == move.fromX &&
                    it.y == move.fromY
        } ?: return state

        val friendlyOnTarget = state.units.any {
            it.x == move.toX && it.y == move.toY && it.player == move.player
        }
        if (friendlyOnTarget) return state

        val enemyOnTarget = state.units.firstOrNull {
            it.x == move.toX && it.y == move.toY &&
                    it.player != move.player &&
                    it.type != UnitType.SKELETON
        }

        if (enemyOnTarget != null) {
            // Basis-Angriff: nicht combat-faehig, direkt zerstoeren.
            // Angreifer ueberlebt immer und uebernimmt die Position.
            if (enemyOnTarget.type == UnitType.BASE) {
                state.units.remove(enemyOnTarget)
                unit.x = move.toX
                unit.y = move.toY
                switchTurn(state)
                checkWinCondition(state)
                return state
            }

            val result = combatService.resolveCombat(unit, enemyOnTarget)
            combatService.applyCombatResult(result, unit, enemyOnTarget)
            if (!result.defenderSurvived) state.units.remove(enemyOnTarget)
            if (!result.attackerSurvived) state.units.remove(unit)
            switchTurn(state)
            checkWinCondition(state)
            return state
        }

        unit.x = move.toX
        unit.y = move.toY

        switchTurn(state)
        checkWinCondition(state)
        return state
    }

    fun handleMove(move: Move): GameState = handleMove(this.gameState, move)

    /**
     * Prueft ob das Match nach der letzten Mutation beendet ist.
     *
     * Win-Condition basiert auf BASE-Existenz (#104) und ist aktuell
     * nur fuer [GameMode.DUAL_VALLEY] aktiv. Fuer TRIAD/BATTLEFIELD
     * muessen erst BASE-Positionen designt werden — siehe TODOs in
     * den entsprechenden Start-Methoden.
     *
     * Regeln:
     *  - Genau ein Spieler hat noch eine BASE → dieser gewinnt
     *  - Keine BASE mehr → Unentschieden (z.B. beide in derselben Aktion
     *    zerstoert)
     *  - Sonst: Spiel laeuft weiter
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
     * Naechsten Spieler an die Reihe nehmen.
     *
     * Konsolidiert:
     *  - 2-Spieler-Alternation (deckt alle alten Tests ab) und
     *    zyklische Rotation fuer 3/4 Spieler aus #51
     *  - applyUpkeep beim Runden-Wrap-Around aus #60 (Farm-Einkommen +
     *    Unterhalt sobald wieder Spieler 1 dran ist)
     */
    private fun switchTurn(state: GameState) {
        if (state.players.isEmpty()) return

        val wasFirstBefore = state.currentTurn == state.players[0].name

        if (state.players.size == 2) {
            val p1 = state.players[0]
            val p2 = state.players[1]
            state.currentTurn = if (state.currentTurn == p1.name) p2.name else p1.name
        } else {
            // 3/4 Spieler: zyklische Rotation
            val currentIndex = state.players.indexOfFirst { it.name == state.currentTurn }
            state.currentTurn = if (currentIndex != -1) {
                state.players[(currentIndex + 1) % state.players.size].name
            } else {
                state.players.firstOrNull()?.name
            }
        }

        // Runden-Wrap-Around: Wenn wir gerade NICHT bei Spieler 1 waren
        // und JETZT bei ihm sind, ist eine Runde voll → Upkeep.
        if (!wasFirstBefore && state.currentTurn == state.players[0].name) {
            applyUpkeep(state)
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
        state.currentTurn = null
        state.status = GameStatus.WAITING_FOR_PLAYERS
        println("Service: GAME INITIALIZED - Everything cleared")
        return state
    }

    fun initializeGame(): GameState = initializeGame(this.gameState)

    /**
     * SPIELER BEHALTEN — fuer /test/reset.
     *
     * Erzeugt fuer jeden verbliebenen Spieler neue Start-Einheiten an
     * festen Positionen. BASE wird nur fuer DUAL_VALLEY mit 2 Spielern
     * platziert (entsprechend [startDualValleyGame]).
     */
    fun resetToStartCondition(state: GameState): GameState = synchronized(state.lock) {
        state.units.clear()

        state.players.forEachIndexed { index, player ->
            val startX = if (index == 0) 2 else 5
            val startY = if (index == 0) 2 else 5

            UnitType.entries
                .filter { it != UnitType.SKELETON && it != UnitType.BASE }
                .forEachIndexed { typeIndex, type ->
                    state.units.add(
                        GameUnit(
                            player = player.name,
                            x = startX + typeIndex,
                            y = startY,
                            type = type
                        )
                    )
                }

            // BASE nur fuer DUAL_VALLEY mit 2 Spielern
            if (state.gameMode == GameMode.DUAL_VALLEY && state.players.size == 2) {
                val basePos = if (index == 0) BASE_POSITION_P1 else BASE_POSITION_P2
                state.units.add(GameUnit(player.name, basePos.first, basePos.second, UnitType.BASE))
            }
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
     * Hybrid-Verhalten:
     *  - DUAL_VALLEY: nutzt [checkWinCondition] (BASE-basiert) — der
     *    verbleibende Spieler gewinnt, wenn nur noch seine Basis steht
     *  - TRIAD/BATTLEFIELD: Fallback auf 51-Verhalten (jeder Disconnect
     *    beendet das Spiel, bis Win-Condition fuer Multi-Mode designt
     *    ist)
     */
    fun handleDisconnect(state: GameState, sessionId: String): GameState = synchronized(state.lock) {
        val player = state.players.find { it.sessionId == sessionId }
            ?: return state

        state.players.remove(player)
        state.units.removeIf { it.player == player.name }

        // Win-Condition fuer DUAL_VALLEY pruefen
        checkWinCondition(state)

        // Fallback fuer Modi ohne BASE-Win: alter 51-Pfad
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
     *
     * Schreibt Farm-Einkommen den Spielern gut und zieht Unterhalt fuer
     * lebende Einheiten ab (arithmetische Reihe: 1. Einheit 3 Gold,
     * jede weitere +1). Bei Insolvenz: Gold auf 0, alle lebenden
     * Truppen werden zu SKELETONs.
     */
    private fun applyUpkeep(state: GameState) {
        state.players.forEach { player ->
            // Farm-Einkommen
            player.gold += player.farms * FARM_INCOME_PER_ROUND

            // Lebende Einheiten (Skelette und Basen zaehlen nicht)
            val playerUnits = state.units.filter {
                it.player == player.name && it.type != UnitType.SKELETON && it.type != UnitType.BASE
            }
            val unitCount = playerUnits.size

            // Unterhalt: 1. Einheit 3 Gold, jede weitere +1
            val upkeep = (0 until unitCount).sumOf { 3 + it }

            if (player.gold >= upkeep) {
                player.gold -= upkeep
            } else {
                // Insolvenz: alle lebenden Truppen → SKELETON
                player.gold = 0
                playerUnits.forEach { it.type = UnitType.SKELETON }
            }
        }

        // Insolvenz koennte die Win-Condition triggern
        checkWinCondition(state)
    }

    /**
     * Kauft eine Farm fuer den Spieler (#60).
     *
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
