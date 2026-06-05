package at.aau.hexabrawl.websocketserver.model

import org.springframework.stereotype.Service

@Service
class GameService(
    private val combatService: CombatService
) {

    val gameState = GameState()

    companion object {
        const val MAX_PLAYERS = 2

        // Maximale Hex-Distanz, die eine Einheit pro Zug zuruecklegen darf.
        // Spielregel: jede Einheit darf bis zu 2 Felder weit ziehen.
        const val MAX_MOVE_DISTANCE = 2

        // Basis-Positionen für DUAL_VALLEY-Modus (8x8-Grid).
        // Bewusst an die Grid-Raender gesetzt, damit haeufige Test-Move-Ziele
        // wie (6,6) oder (3,1) frei bleiben - sonst blockiert friendlyOnTarget
        // legitime Moves der bestehenden Test-Suite.
        val BASE_POSITION_P1: Pair<Int, Int> = Pair(3, 0)
        val BASE_POSITION_P2: Pair<Int, Int> = Pair(6, 7)

        // Board-Dimensionen fuer DUAL_VALLEY (Standard-Modus).
        // Wird spaeter durch GameMode-spezifische Werte ersetzt sobald
        // der Multisession-PR gemergt ist.
        const val BOARD_ROWS = 10
        const val BOARD_COLS = 10

        // Startgebiete pro Spieler: Felder unter den Start-Einheiten + Basis.
        val START_TERRITORY_P1: List<Pair<Int, Int>> = listOf(
            2 to 2, 3 to 2, 4 to 2,  // ARCHER, INFANTRY, CAVALRY
            3 to 0                    // BASE
        )
        val START_TERRITORY_P2: List<Pair<Int, Int>> = listOf(
            5 to 5, 6 to 5, 7 to 5,
            6 to 7
        )
    }

    fun handleJoin(state: GameState, playerName: String, sessionId:String=""): GameState = synchronized(state.lock) {
        // Spieler hinzufügen, falls noch nicht vorhanden und Platz ist

        if (!state.players.any{it.name == playerName} && state.players.size < MAX_PLAYERS) {
            val color = if (state.players.isEmpty()) PlayerColor.RED else PlayerColor.BLUE
            state.players.add(Player(playerName, sessionId,color))
            println("JOIN: $playerName")
        }

        // Automatischer Start bei 2 Spielern
        if (state.players.size == 2 && state.units.isEmpty()) {
            val p1 = state.players[0]
            val p2 = state.players[1]

            // Start-Einheiten setzen
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

            UnitType.entries
                .filter { it != UnitType.SKELETON && it != UnitType.BASE }
                .forEachIndexed { index, type ->
                    val (x1, y1) = startPositionsP1[index]
                    val (x2, y2) = startPositionsP2[index]

                    state.units.add(GameUnit(p1.name, x1, y1, type))
                    state.units.add(GameUnit(p2.name, x2, y2, type))
                }

            // Basis pro Spieler platzieren - das ist die Sieg-relevante Entitaet:
            // wer die gegnerische Basis erreicht, gewinnt sofort (siehe Folge-Sub-Issue).
            state.units.add(GameUnit(p1.name, BASE_POSITION_P1.first, BASE_POSITION_P1.second, UnitType.BASE))
            state.units.add(GameUnit(p2.name, BASE_POSITION_P2.first, BASE_POSITION_P2.second, UnitType.BASE))

            state.currentTurn = p1.name
            state.status = GameStatus.IN_PROGRESS
            println("Service: GAME STARTED")
        }
        return state

    }

    //Bridge Method handleJoin
    fun handleJoin(
        playerName: String,
        sessionId: String = ""
    ): GameState = handleJoin(this.gameState, playerName, sessionId)

    fun handleMove(state: GameState, move: Move): GameState = synchronized(state.lock) {
        if (state.status != GameStatus.IN_PROGRESS) return state
        if (move.player != state.currentTurn) return state

        // Basen sind stationaer - sie koennen nicht via Move-Befehl bewegt werden.
        if (move.type == UnitType.BASE) return state

        val unit = state.units.firstOrNull {
            it.player == move.player &&
                    it.type == move.type &&
                    it.type != UnitType.SKELETON &&
                    it.x == move.fromX &&
                    it.y == move.fromY
        } ?: return state

        // Distanz pruefen: zu weite Zuege werden abgelehnt, ebenso "Null-Zuege"
        // auf das Startfeld. Der Controller wandelt das in INVALID_MOVE um.
        val distance = HexDistance.between(move.fromX, move.fromY, move.toX, move.toY)
        if (distance == 0 || distance > MAX_MOVE_DISTANCE) return state

        // Eine Einheit darf pro Runde nur einmal bewegt werden.
        // Verhindert dass ein Spieler dieselbe Einheit zweimal in einer Runde
        // zieht.
        if (unit.hasMovedThisTurn) return state

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
            // Basis-Angriff: Basen sind nicht combat-faehig - sie werden ohne
            // Stein-Schere-Papier-Resolution direkt zerstoert. Der Angreifer
            // ueberlebt immer und zieht auf die Basis-Position.
            // checkWinCondition triggert anschliessend den Sieg.
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

    /**
     * Checks whether the match has ended after the latest mutation.
     *
     * Win condition is based on BASE existence:
     * - If exactly one player still has a BASE on the board, that player
     *   is declared the winner.
     * - If no player has a BASE left (e.g. both bases were destroyed in
     *   the same sequence of events), the match ends as a draw.
     * - Otherwise the game continues.
     *
     * Regular unit losses (ARCHER/INFANTRY/CAVALRY) no longer end the
     * match - players can lose all their regular units and the game
     * continues as long as their base stands.
     *
     * Only runs while the game is IN_PROGRESS, so calling it from
     * non-success paths or before game start is harmless.
     */
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
                // >= 2 players still have a base: game continues.
            }
        }
    }

    //Bridge Method handleMove
    fun handleMove(
        move: Move
    ): GameState = handleMove(this.gameState, move)

    /**
     * Beendet die Runde des angegebenen Spielers freiwillig.
     *
     * Wird vom Controller aufgerufen wenn der Spieler auf "Runde beenden"
     * klickt - auch wenn noch nicht alle Einheiten bewegt wurden. Nur der
     * aktuelle Spieler kann seinen eigenen Turn beenden, andere werden
     * ignoriert.
     */
    fun endTurn(state: GameState, playerName: String): GameState = synchronized(state.lock) {
        if (state.status != GameStatus.IN_PROGRESS) return state
        if (state.currentTurn != playerName) return state
        switchTurn(state)
        return state
    }

    //Bridge Method endTurn
    fun endTurn(playerName: String): GameState = endTurn(this.gameState, playerName)


    /**
     * Erzeugt alle Felder des Boards und weist die Startgebiete an die
     * beiden Spieler zu. Vor dem Aufruf muessen beide Spieler in
     * state.players existieren.
     *
     * Wird beim Spielstart und nach jedem Reset aufgerufen.
     */
    private fun initializeBoard(state: GameState) {
        state.fields.clear()

        // Alle Felder erzeugen, default neutral (owner = null).
        for (x in 0 until BOARD_COLS) {
            for (y in 0 until BOARD_ROWS) {
                state.fields.add(Field(x, y))
            }
        }

        // Startgebiete den Spielern zuweisen.
        val p1 = state.players[0]
        val p2 = state.players[1]

        START_TERRITORY_P1.forEach { (x, y) ->
            state.fields.first { it.x == x && it.y == y }.owner = p1.name
        }
        START_TERRITORY_P2.forEach { (x, y) ->
            state.fields.first { it.x == x && it.y == y }.owner = p2.name
        }
    }

    private fun switchTurn(state: GameState) {

        val (p1, p2) = state.players

        state.currentTurn =
            if (state.currentTurn == p1.name)
                p2.name
            else
                p1.name

        // Neue Runde fuer den naechsten Spieler: alle Einheiten duerfen wieder ziehen.
        state.units.forEach { it.hasMovedThisTurn = false }
    }

    /**
     * Prueft ob der gegebene Spieler in dieser Runde bereits alle seine
     * bewegbaren Einheiten gezogen hat. SKELETONs und BASEs zaehlen nicht
     * als bewegbar.
     *
     * Gibt `false` zurueck wenn der Spieler keine bewegbaren Einheiten
     * besitzt - in dem Fall muss der Turn manuell ueber endTurn beendet
     * werden, sonst wuerde der Turn sofort wechseln ohne dass ein Move
     * stattfand.
     */
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
     * als bewegt (falls sie noch lebt) und switcht automatisch den Turn
     * wenn alle bewegbaren Einheiten des aktuellen Spielers gezogen haben.
     */
    private fun finishMove(state: GameState, unit: GameUnit, playerName: String) {
        // Falls die Einheit den Move ueberlebt hat, als bewegt markieren.
        // Nach Combat kann sie aus state.units entfernt worden sein.
        if (unit in state.units) {
            unit.hasMovedThisTurn = true
        }
        // Auto-Switch wenn alle Einheiten gezogen haben.
        if (allMovableUnitsHaveMoved(state, playerName)) {
            switchTurn(state)
        }
    }

    // WICHTIG FÜR TEST  Nur den aktuellen Stand lesen
    fun getCurrentState(state: GameState): GameState = synchronized(state.lock) {
        return state
    }

    //Bridge Method getCurrentState
    fun getCurrentState(): GameState =
        getCurrentState(this.gameState)



    // ALLES AUF NULL - Für /test/init
    fun initializeGame(state: GameState): GameState = synchronized(state.lock) {
        state.players.clear()
        state.units.clear()
        state.currentTurn = null
        state.status = GameStatus.WAITING_FOR_PLAYERS
        println("Service: GAME INITIALIZED - Everything cleared")
        return state
    }

    //Bridge Method initializeGame
    fun initializeGame(): GameState = initializeGame(this.gameState)

    // SPIELER BEHALTEN - Für /test/reset
    fun resetToStartCondition(state: GameState): GameState = synchronized(state.lock) {
        state.units.clear() // Alte Einheiten löschen

        // Für jeden verbliebenen Spieler eine neue Start-Einheit erstellen
        state.players.forEachIndexed { index, player ->
            // Jedem Spieler eine feste Startposition zuordnen
            // Beispiel: Spieler 1 bei (0,0), Spieler 2 bei (5,5) - Werte an Grid anpassen!
            val startX = if (index == 0) 2 else 5
            val startY = if (index == 0) 2 else 5

            UnitType.entries
                .filter { it != UnitType.SKELETON && it != UnitType.BASE }
                .forEachIndexed { typeIndex, type ->
                    val newUnit = GameUnit(
                        player = player.name,
                        x = startX + typeIndex,
                        y = startY,
                        type = type
                    )
                    state.units.add(newUnit)
                }

            // Basis pro Spieler an der vordefinierten Position wiederherstellen
            val basePos = if (index == 0) BASE_POSITION_P1 else BASE_POSITION_P2
            state.units.add(GameUnit(player.name, basePos.first, basePos.second, UnitType.BASE))
        }

        state.currentTurn = state.players.firstOrNull()?.name
        state.status = GameStatus.IN_PROGRESS

        println("Service: Reset - Units for ${state.players} recreated at start positions.")
        return state
    }

    //Bridge Method resetToStartCondition
    fun resetToStartCondition(): GameState = resetToStartCondition(this.gameState)

    fun handleDisconnect(state: GameState, sessionId: String): GameState = synchronized(state.lock) {
        val player = state.players.find { it.sessionId == sessionId }
            ?: return state

        // Spieler und seine Units entfernen
        state.players.remove(player)
        state.units.removeIf { it.player == player.name }

        // Win-Bedingung neu auswerten - kann das Match beenden, wenn jetzt nur
        // noch ein (oder kein) Spieler mit Einheiten übrig ist.
        checkWinCondition(state)

        if (state.status == GameStatus.FINISHED) {
            println("Service: GAME FINISHED - ${player.name} disconnected, winner: ${state.winner}")
        } else {
            println("Service: PLAYER LEFT - ${player.name} disconnected")
        }

        return state
    }

    //Bridge Method handleDisconnect
    fun handleDisconnect(
        sessionId: String
    ): GameState = handleDisconnect(this.gameState, sessionId)

}
