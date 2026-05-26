package at.aau.hexabrawl.websocketserver.model

import org.springframework.stereotype.Service

@Service
class GameService(
    private val combatService: CombatService
) {

    val gameState = GameState()
    val lock = Any()

    companion object {
        const val MAX_PLAYERS = 2
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

            UnitType.entries.filter { it != UnitType.SKELETON }.forEachIndexed { index, type ->
                val (x1, y1) = startPositionsP1[index]
                val (x2, y2) = startPositionsP2[index]

                state.units.add(GameUnit(p1.name, x1, y1, type))
                state.units.add(GameUnit(p2.name, x2, y2, type))
            }

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
            val result = combatService.resolveCombat(unit, enemyOnTarget)
            combatService.applyCombatResult(result, unit, enemyOnTarget)
            if (!result.defenderSurvived) state.units.remove(enemyOnTarget)
            if (!result.attackerSurvived) state.units.remove(unit)
            switchTurn(state)
            return state
        }

        unit.x = move.toX
        unit.y = move.toY

        val (p1, p2) = state.players
        state.currentTurn = if (state.currentTurn == p1.name) p2.name else p1.name

        return state
    }

    //Bridge Method handleMove
    fun handleMove(
        move: Move
    ): GameState = handleMove(this.gameState, move)

    private fun switchTurn() {
        val (p1, p2) = gameState.players
        gameState.currentTurn = if (gameState.currentTurn == p1.name) p2.name else p1.name
    }

    private fun switchTurn(state: GameState) {

        val (p1, p2) = state.players

        state.currentTurn =
            if (state.currentTurn == p1.name)
                p2.name
            else
                p1.name
    }

    // WICHTIG FÜR TEST  Nur den aktuellen Stand lesen
    fun getCurrentState(): GameState = synchronized(lock) {
        return gameState
    }

    // ALLES AUF NULL - Für /test/init
    fun initializeGame(): GameState = synchronized(lock) {
        gameState.players.clear()
        gameState.units.clear()
        gameState.currentTurn = null
        gameState.status = GameStatus.WAITING_FOR_PLAYERS
        println("Service: GAME INITIALIZED - Everything cleared")
        return gameState
    }

    // SPIELER BEHALTEN - Für /test/reset
    fun resetToStartCondition(): GameState = synchronized(lock) {
        gameState.units.clear() // Alte Einheiten löschen

        // Für jeden verbliebenen Spieler eine neue Start-Einheit erstellen
        gameState.players.forEachIndexed { index, player ->
            // Jedem Spieler eine feste Startposition zuordnen
            // Beispiel: Spieler 1 bei (0,0), Spieler 2 bei (5,5) - Werte an Grid anpassen!
            val startX = if (index == 0) 2 else 5
            val startY = if (index == 0) 2 else 5

            UnitType.entries.filter { it != UnitType.SKELETON }.forEachIndexed { typeIndex, type ->
                val newUnit = GameUnit(
                    player = player.name,
                    x = startX + typeIndex,
                    y = startY,
                    type = type
                )
                gameState.units.add(newUnit)
            }
        }

        gameState.currentTurn = gameState.players.firstOrNull()?.name
        gameState.status = GameStatus.IN_PROGRESS

        println("Service: Reset - Units for ${gameState.players} recreated at start positions.")
        return gameState
    }

    fun handleDisconnect(sessionId: String): GameState = synchronized(lock) {
        val player = gameState.players.find { it.sessionId == sessionId }
            ?: return gameState

        // Spieler und seine Units entfernen
        gameState.players.remove(player)
        gameState.units.removeIf { it.player == player.name }

        // Status anpassen
        if (gameState.status == GameStatus.IN_PROGRESS) {
            gameState.status = GameStatus.FINISHED
            gameState.currentTurn = null
            println("Service: GAME FINISHED - ${player.name} disconnected")
        } else {
            println("Service: PLAYER LEFT - ${player.name} disconnected while waiting")
        }

        return gameState
    }

}
