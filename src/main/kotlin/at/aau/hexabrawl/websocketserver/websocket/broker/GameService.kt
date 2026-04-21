package at.aau.hexabrawl.websocketserver.websocket.broker

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
        if (!gameState.players.contains(playerName)) {
            if (gameState.players.size < MAX_PLAYERS) {
                gameState.players.add(playerName)
                println("Service JOIN: $playerName")
            }
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

    // Zum Testen
    fun resetGame(): GameState = synchronized(lock) {
        gameState.players.clear()
        gameState.units.clear()
        gameState.currentTurn = null
        gameState.status = GameStatus.WAITING_FOR_PLAYERS
        println("Service: GAME RESET")
        return gameState
    }

}
