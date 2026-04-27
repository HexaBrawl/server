package at.aau.hexabrawl.websocketserver.model

enum class GameStatus {
    WAITING_FOR_PLAYERS,
    IN_PROGRESS,
    FINISHED
}

data class GameState(
    val players: MutableList<Player> = mutableListOf(),
    val units: MutableList<GameUnit> = mutableListOf(),
    var currentTurn: Player? = null,
    var status: GameStatus = GameStatus.WAITING_FOR_PLAYERS
)