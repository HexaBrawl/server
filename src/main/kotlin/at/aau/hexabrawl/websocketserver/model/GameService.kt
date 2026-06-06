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

    /**
     * Adds a player to the given game state if there is still room available.
     *
     * The maximum number of players is determined by the current
     * [GameMode] stored in the provided [GameState].
     *
     * When the required number of players for the selected game mode
     * has joined, the game is started automatically and the initial
     * unit positions are created.
     *
     * This method is thread-safe and synchronizes on the state's lock.
     *
     * @param state The game state to modify.
     * @param playerName The name of the joining player.
     * @param sessionId The WebSocket session identifier of the player.
     * @return The updated game state.
     */
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


    /**
     * Starts a game according to the game mode stored in the provided state.
     *
     * Depending on the selected [GameMode], the corresponding initial
     * unit setup is created and the game is transitioned to
     * [GameStatus.IN_PROGRESS].
     *
     * Supported game modes:
     * - [GameMode.DUAL_VALLEY] (2 players)
     * - [GameMode.TRIAD_OUTPOST] (3 players)
     * - [GameMode.BATTLEFIELD_PEAKS] (4 players)
     *
     * @param state The game state to initialize.
     */
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


    /**
     * Initializes a DUAL_VALLEY game with two players.
     *
     * Creates the default starting units for both players at their
     * predefined starting positions on opposite sides of the map.
     *
     * After all units have been placed, the first player receives the
     * opening turn and the game status is set to
     * [GameStatus.IN_PROGRESS].
     *
     * @param state The game state to initialize.
     */
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


    /**
     * Initializes a TRIAD_OUTPOST game with three players.
     *
     * Creates the starting units for all three players at predefined
     * positions arranged around the map center in a triangular layout.
     * This setup provides each player with an equal distance to the
     * center and to the opposing players.
     *
     * After all units have been placed, the first player receives the
     * opening turn and the game status is set to
     * [GameStatus.IN_PROGRESS].
     *
     * @param state The game state to initialize.
     */
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

    /**
     * Initializes a BATTLEFIELD_PEAKS game with four players.
     *
     * Creates the starting units for all four players at predefined
     * positions arranged in a cross-shaped layout around the map.
     * Each player starts from a different edge of the battlefield,
     * providing a balanced setup with equal access to the central area.
     *
     * After all units have been placed, the first player receives the
     * opening turn and the game status is set to
     * [GameStatus.IN_PROGRESS].
     *
     * @param state The game state to initialize.
     */
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

    /*
    * Executes a player's move and updates the game state accordingly.
    *
    * The move is validated against the current game rules. If the move
    * is valid, the selected unit is moved to its new position and the
    * turn is passed to the next player.
    *
    * Turn rotation supports both the classic two-player mode and the
    * multiplayer game modes with three or four players.
    *
    * This method is thread-safe and synchronizes on the state's lock.
    *
    * @param state The game state to modify.
    * @param move The move to execute.
    * @return The updated game state.

    */
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


    /**
     * Advances the turn to the next player.
     *
     * For games with two players, the original alternating turn logic
     * is used to preserve the behaviour of the classic game mode and
     * existing tests.
     *
     * For games with three or four players, turns are rotated through
     * the player list in a circular manner. After the last player has
     * taken a turn, the first player becomes active again.
     *
     * If the current player cannot be found in the player list, the
     * first player is selected as a fallback.
     *
     * @param state The game state whose active player should be updated.
     */
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

    /**
     * Removes a player from the game based on the associated session ID.
     *
     * If a matching player is found, the player and all of their units
     * are removed from the game state.
     *
     * If the disconnected player was part of an active game, the game is
     * marked as [GameStatus.FINISHED].
     *
     * This method is thread-safe and synchronizes on the state's lock.
     *
     * @param state The game state to update.
     * @param sessionId The session identifier of the disconnected player.
     * @return The updated game state.
     */
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
