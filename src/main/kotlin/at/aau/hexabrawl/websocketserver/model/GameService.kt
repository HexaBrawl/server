package at.aau.hexabrawl.websocketserver.model

import org.springframework.stereotype.Service

@Service
class GameService {

    val gameState = GameState()
    val lock = Any()

    companion object {
        const val MAX_PLAYERS = 2
    }

    fun handleJoin(playerName: String): GameState = synchronized(lock) {
        // Spieler hinzufügen, falls noch nicht vorhanden und Platz ist

       if (!gameState.players.contains(playerName) && gameState.players.size < MAX_PLAYERS) {
            gameState.players.add(playerName)

        }


        // Automatischer Start bei 2 Spielern
        if (gameState.players.size == 2 && gameState.units.isEmpty()) {
            val p1 = gameState.players[0]
            val p2 = gameState.players[1]

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

            UnitType.values().forEachIndexed { index, type ->
                val (x1, y1) = startPositionsP1[index]
                val (x2, y2) = startPositionsP2[index]

                gameState.units.add(GameUnit(p1, x1, y1, type))
                gameState.units.add(GameUnit(p2, x2, y2, type))
            }

            gameState.currentTurn = p1
            gameState.status = GameStatus.IN_PROGRESS
            println("Service: GAME STARTED")
        }
        return gameState
    }

    /*fun handleMove(move: Move): GameState = synchronized(lock) {
        if (gameState.status != GameStatus.IN_PROGRESS) return gameState
        if (move.player != gameState.currentTurn) return gameState

        val targetOccupied = gameState.units.any {
            it.x == move.toX && it.y == move.toY
        }
        if (targetOccupied) return gameState

        gameState.units.firstOrNull {
            it.player == move.player && it.type == move.type
        }?.apply {
            x = move.toX
            y = move.toY
        }

        // Spielerwechsel
        val (p1, p2) = gameState.players
        gameState.currentTurn = if (gameState.currentTurn == p1) p2 else p1

        return gameState
    }*/

    fun handleMove(move: Move): GameState = synchronized(lock) {
        if (gameState.status != GameStatus.IN_PROGRESS) return gameState
        if (move.player != gameState.currentTurn) return gameState

        val unit = gameState.units.firstOrNull {
            it.player == move.player && it.type == move.type
        } ?: return gameState

        val targetOccupied = gameState.units.any {
            it.x == move.toX &&
                    it.y == move.toY &&
                    !(it.player == move.player && it.type == move.type)
        }

        if (targetOccupied) return gameState

        unit.x = move.toX
        unit.y = move.toY

        val (p1, p2) = gameState.players
        gameState.currentTurn = if (gameState.currentTurn == p1) p2 else p1

        return gameState
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
        gameState.players.forEachIndexed { index, playerName ->
            // Jedem Spieler eine feste Startposition zuordnen
            // Beispiel: Spieler 1 bei (0,0), Spieler 2 bei (5,5) - Werte an Grid anpassen!
            val startX = if (index == 0) 2 else 5
            val startY = if (index == 0) 2 else 5

            UnitType.values().forEachIndexed { typeIndex, type ->
                val newUnit = GameUnit(
                    player = playerName,
                    x = startX + typeIndex,
                    y = startY,
                    type = type
                )
                gameState.units.add(newUnit)
            }

        }

        gameState.currentTurn = gameState.players.firstOrNull()
        gameState.status = GameStatus.IN_PROGRESS

        println("Service: Reset - Units for ${gameState.players} recreated at start positions.")
        return gameState
    }




}
