package at.aau.hexabrawl.websocketserver.model

import org.springframework.stereotype.Service

@Service
class GameService(
    private val combatService: CombatService
) {

    val gameState = GameState()

    companion object {
        const val MAX_PLAYERS = 2
    }

    fun handleJoin(state: GameState, playerName: String, sessionId:String=""): GameState = synchronized(state.lock) {
        // Spieler hinzufügen, falls noch nicht vorhanden und Platz ist
        if (!state.players.any{it.name == playerName} && state.players.size < state.gameMode.maxPlayers) {

            // Dynamische Farbauswahl für bis zu 4 Spieler anhand der aktuellen Listengröße
            val colors = listOf(PlayerColor.RED, PlayerColor.BLUE, PlayerColor.GREEN, PlayerColor.YELLOW)
            val color = colors.getOrElse(state.players.size) { PlayerColor.RED }

            state.players.add(Player(playerName, sessionId, color))
            println("JOIN: $playerName mit Farbe $color")
        }

        // Automatischer Start bei Erreichen der maximalen Spieleranzahl des Modus
        if (state.players.size == state.gameMode.maxPlayers && state.units.isEmpty()) {
            println("players=${state.players.size}, max=${state.gameMode.maxPlayers}")
            startGame(state)
        }
        return state
    }


    fun startGame(state: GameState) {
        when(state.gameMode) {
            GameMode.DUAL_VALLEY -> startDualValleyGame(state)
            GameMode.TRIAD_OUTPOST -> startTriadOutpostGame(state)
            GameMode.BATTLEFIELD_PEAKS -> startBattlefieldPeaksGame(state)
        }
    }

    //Bridge Method handleJoin
    fun handleJoin(
        playerName: String,
        sessionId: String = ""
    ): GameState = handleJoin(this.gameState, playerName, sessionId)


    private fun startDualValleyGame(state: GameState)
    {
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


    private fun startTriadOutpostGame(state: GameState) {
        val p1 = state.players[0]
        val p2 = state.players[1]
        val p3 = state.players[2]

        // Start-Einheiten für 3 Spieler setzen
        // Index 0: ARCHER, Index 1: INFANTRY, Index 2: CAVALRY
        val startPositionsP1 = listOf(
            Pair(4, 2),  // ARCHER (Süden)
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

        UnitType.entries.filter { it != UnitType.SKELETON }.forEachIndexed { index, type ->
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

    private fun startBattlefieldPeaksGame(state: GameState) {
        val p1 = state.players[0]
        val p2 = state.players[1]
        val p3 = state.players[2]
        val p4 = state.players[3]

        // Start-Einheiten für 4 Spieler setzen
        // Index 0: ARCHER, Index 1: INFANTRY, Index 2: CAVALRY
        val startPositionsP1 = listOf(
            Pair(4, 1),  // ARCHER (Süden)
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

        UnitType.entries.filter { it != UnitType.SKELETON }.forEachIndexed { index, type ->
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

        // Ersetzt die alte 2-Spieler-Zuweisung durch den zentralen Rundenwechsel
        switchTurn(state)
        return state
    }

    //Bridge Method handleMove
    fun handleMove(
        move: Move
    ): GameState = handleMove(this.gameState, move)


    private fun switchTurn(state: GameState) {
        // Falls das Spiel im klassischen 2-Spieler-Modus ist (deckt alle alten Tests ab)
        if (state.players.size == 2) {
            val p1 = state.players[0]
            val p2 = state.players[1]

            // alte, originale Logik
            state.currentTurn = if (state.currentTurn == p1.name) p2.name else p1.name
            return
        }

        // Dynamische Rotation für 3 oder 4 Spieler im echten Spiel
        if (state.players.isNotEmpty()) {
            val currentIndex = state.players.indexOfFirst { it.name == state.currentTurn }
            if (currentIndex != -1) {
                val nextIndex = (currentIndex + 1) % state.players.size
                state.currentTurn = state.players[nextIndex].name
            } else {
                // Falls der aktuelle Turn aus irgendeinem Grund nicht matcht, fängt der Erste an
                state.currentTurn = state.players.firstOrNull()?.name
            }
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

            UnitType.entries.filter { it != UnitType.SKELETON }.forEachIndexed { typeIndex, type ->
                val newUnit = GameUnit(
                    player = player.name,
                    x = startX + typeIndex,
                    y = startY,
                    type = type
                )
                state.units.add(newUnit)
            }
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

        // Status anpassen
        if (state.status == GameStatus.IN_PROGRESS) {
            state.status = GameStatus.FINISHED
            state.currentTurn = null
            println("Service: GAME FINISHED - ${player.name} disconnected")
        } else {
            println("Service: PLAYER LEFT - ${player.name} disconnected while waiting")
        }

        return state
    }

    //Bridge Method handleDisconnect
    fun handleDisconnect(
        sessionId: String
    ): GameState = handleDisconnect(this.gameState, sessionId)

}
