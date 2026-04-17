package at.aau.hexabrawl.websocketserver.websocket.broker

/*class GameState(
    var players: MutableList<String> = mutableListOf(),
    var currentTurn: String? = null,
    var units: MutableList<GameUnit> = mutableListOf()
)*/
enum class GameStatus {
    WAITING_FOR_PLAYERS,
    IN_PROGRESS,
    FINISHED
}

data class GameState(
    val players: MutableList<String> = mutableListOf(),
    val units: MutableList<GameUnit> = mutableListOf(),
    var currentTurn: String? = null,
    var status: GameStatus = GameStatus.WAITING_FOR_PLAYERS
)