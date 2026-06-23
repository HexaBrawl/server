package at.aau.hexabrawl.websocketserver.service

import at.aau.hexabrawl.websocketserver.model.ErrorCode
import at.aau.hexabrawl.websocketserver.model.GameState
import at.aau.hexabrawl.websocketserver.model.GameStatus
import at.aau.hexabrawl.websocketserver.model.Player
import at.aau.hexabrawl.websocketserver.model.PlayerColor
import at.aau.hexabrawl.websocketserver.model.UnitType
import org.springframework.stereotype.Service

/**
 * Spieler-Lifecycle: Join, Farb-Validierung, Soft-/Hard-Disconnect, Win-Condition.
 *
 * Beim Join wird automatisch das Spiel gestartet (via BoardService), sobald
 * die Mode-Spielerzahl erreicht ist.
 *
 * Soft-Disconnect markiert nur ({@code connected = false}), Hard-Delete
 * entfernt den Spieler endgueltig — neutralisiert Felder und Loescht Units.
 *
 * Die Win-Condition wird nach jeder Eliminierung geprueft.
 */
@Service
class PlayerService(
    private val boardService: BoardService
) {

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

            state.players.add(Player(playerName, sessionId, assignedColor, EconomyService.STARTING_GOLD))
            println("JOIN: $playerName (color: $assignedColor)")
        }

        // Auto-Start bei Erreichen der Mode-Spielerzahl
        if (state.players.size == state.gameMode.maxPlayers && state.units.isEmpty()) {
            println("players=${state.players.size}, max=${state.gameMode.maxPlayers}")
            boardService.startGame(state)
        }
        return state
    }

    fun isColorTaken(state: GameState, color: PlayerColor): Boolean = synchronized(state.lock) {
        return state.players.any { it.color == color }
    }

    fun handleReconnect(state: GameState, playerName: String, newSessionId: String): ReconnectResult =
        synchronized(state.lock) {
            val player = state.players.find { it.name == playerName }
            if (player == null || player.connected) {
                return ReconnectResult.Rejected(ErrorCode.RECONNECT_REJECTED, "Kein wartender Spieler mit diesem Namen.")
            }
            player.connected = true
            player.disconnectedAt = null
            player.sessionId = newSessionId
            return ReconnectResult.Reconnected(state)
        }

    /**
     * Soft-Disconnect: markiert den Spieler als nicht-verbunden und merkt
     * sich den Zeitpunkt. Wird vom Scheduled Cleanup nach Grace ausgewertet.
     */
    fun handleDisconnect(state: GameState, sessionId: String): GameState = synchronized(state.lock) {
        val player = state.players.find { it.sessionId == sessionId } ?: return state
        player.connected = false
        player.disconnectedAt = System.currentTimeMillis()
        println("Service: SOFT DISCONNECT - ${player.name}")
        return state
    }

    /**
     * Hard-Delete eines Spielers. Wird nach Grace-Period (Scheduled Cleanup)
     * oder /leave aufgerufen. Raeumt pendingGift auf, eliminiert den Spieler,
     * prueft Win-Condition.
     */
    fun hardDelete(state: GameState, player: Player): Unit = synchronized(state.lock) {
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

    /**
     * Prueft die Win-Condition: wer hat noch eine BASE?
     *  - 1 Spieler mit BASE → Win
     *  - 0 Spieler mit BASE → Draw
     *  - >= 2 mit BASE → Spiel laeuft weiter
     *
     * Eliminiert auch Spieler ohne BASE aus der players-Liste.
     */
    fun checkWinCondition(state: GameState) {
        if (state.status != GameStatus.IN_PROGRESS) return

        val playersWithBase = state.units
            .filter { it.type == UnitType.BASE }
            .map { it.player }
            .toSet()

        val playersToEliminate = state.players
            .map { it.name }
            .filter { it !in playersWithBase }

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
            else -> { /* >= 2: Spiel laeuft weiter. */ }
        }
    }

    /**
     * Entfernt einen Spieler restlos: Zug-Weitergabe, Spieler-Removal, Unit-Loeschung,
     * Felder neutralisieren. Wird auch fuer Basis-Verlust verwendet.
     *
     * ACHTUNG: switchTurn-Aufruf muss vom Caller orchestriert werden, weil
     * switchTurn jetzt im TurnService lebt. Hier wird vorerst eine
     * eigene Mini-Implementation gemacht, die ohne Economy-Tick switcht.
     */
    fun eliminatePlayer(state: GameState, playerName: String) {
        // Zug-Weitergabe vor Removal (analog switchTurn ohne Economy-Tick)
        if (state.currentTurn == playerName) {
            val currentIndex = state.players.indexOfFirst { it.name == playerName }
            val nextIndex = if (state.players.size > 1) (currentIndex + 1) % state.players.size else -1
            state.currentTurn = if (nextIndex >= 0 && nextIndex < state.players.size && nextIndex != currentIndex) {
                state.players[nextIndex].name
            } else {
                state.players.firstOrNull { it.name != playerName }?.name
            }
            state.units.forEach { it.hasMovedThisTurn = false }
        }

        state.players.removeIf { it.name == playerName }
        state.units.removeIf { it.player == playerName }
        state.fields.filter { it.owner == playerName }.forEach { field ->
            field.owner = null
            field.isSkeleton = false
        }
    }
}
