package at.aau.hexabrawl.websocketserver.model

import org.springframework.stereotype.Service

@Service
class GameService {

    val gameState = GameState()
    val lock = Any()

    companion object {
        const val MAX_PLAYERS = 2
    }

    fun handleJoin(playerName: String, sessionId:String=""): GameState = synchronized(lock) {
        // Spieler hinzufügen, falls noch nicht vorhanden und Platz ist

        if (!gameState.players.any{it.name == playerName} && gameState.players.size < MAX_PLAYERS) {
            val color = if (gameState.players.isEmpty()) PlayerColor.RED else PlayerColor.BLUE
            gameState.players.add(Player(playerName, sessionId,color))
        }


        // Automatischer Start bei 2 Spielern
        if (gameState.players.size == 2 && gameState.units.isEmpty()) {
            val p1 = gameState.players[0]
            val p2 = gameState.players[1]

            // Start-Einheiten setzen
            gameState.units.add(GameUnit(p1, 2, 2))
            gameState.units.add(GameUnit(p2, 5, 5))

            gameState.currentTurn = p1
            gameState.status = GameStatus.IN_PROGRESS
            println("Service: GAME STARTED")
        }
        return gameState
    }

    fun handleMove(move: Move): GameState = synchronized(lock) {
        if (gameState.status != GameStatus.IN_PROGRESS) return gameState
        if (move.player != gameState.currentTurn) return gameState

        gameState.units.firstOrNull { it.player == move.player }?.apply {
            x = move.toX
            y = move.toY
        }

        // Spielerwechsel
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
            // Beispiel: Spieler 1 bei (0,0), Spieler 2 bei (5,5) - passe die Werte an dein Grid an!
            val startX = if (index == 0) 2 else 5
            val startY = if (index == 0) 2 else 5

            val newUnit = GameUnit(
                player = playerName,
                x = startX,
                y = startY
            )

            gameState.units.add(newUnit)
        }

        gameState.currentTurn = gameState.players.firstOrNull()
        gameState.status = GameStatus.IN_PROGRESS

        println("Service: Reset - Units for ${gameState.players} recreated at start positions.")
        return gameState
    }




}
