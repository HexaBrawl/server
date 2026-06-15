package at.aau.hexabrawl.websocketserver.model
import com.fasterxml.jackson.annotation.JsonIgnore

/**
 * Lifecycle eines Spiels: warten → laufend → beendet.
 * Wechsel von IN_PROGRESS auf FINISHED triggert der
 * [at.aau.hexabrawl.websocketserver.service.PlayerService] beim
 * Auflösen der Win-Condition.
 */
enum class GameStatus {
    WAITING_FOR_PLAYERS,
    IN_PROGRESS,
    FINISHED
}

/**
 * Vollstaendiger Spielzustand eines Raums.
 *
 * Mutiert durch die Domain-Services; alle Mutationen sind ueber [lock]
 * synchronisiert (per `synchronized(state.lock) { ... }`). Wird als
 * Ganzes via Jackson serialisiert und an /topic/rooms/{roomId}/state
 * gebroadcastet — das Lock-Feld ist daher mit @JsonIgnore versehen.
 */
data class GameState(
    val players: MutableList<Player> = mutableListOf(),
    val units: MutableList<GameUnit> = mutableListOf(),
    val fields: MutableList<Field> = mutableListOf(),
    var currentTurn: String? = null,
    var status: GameStatus = GameStatus.WAITING_FOR_PLAYERS,
    var gameMode: GameMode = GameMode.DUAL_VALLEY,
    var winner: String? = null,
    var pendingGift: PendingGift? = null
)
{
    @JsonIgnore
    val lock: Any = Any()
}
