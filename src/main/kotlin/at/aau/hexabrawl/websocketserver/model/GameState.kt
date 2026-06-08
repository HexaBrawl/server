package at.aau.hexabrawl.websocketserver.model
import com.fasterxml.jackson.annotation.JsonIgnore

enum class GameStatus {
    WAITING_FOR_PLAYERS,
    IN_PROGRESS,
    FINISHED
}

data class GameState(
    val players: MutableList<Player> = mutableListOf(),
    val units: MutableList<GameUnit> = mutableListOf(),
    val fields: MutableList<Field> = mutableListOf(),
    var currentTurn: String? = null,
    var status: GameStatus = GameStatus.WAITING_FOR_PLAYERS,
    var gameMode: GameMode = GameMode.DUAL_VALLEY,
    var winner: String? = null
)
{
    @JsonIgnore
    val lock: Any = Any()
}
