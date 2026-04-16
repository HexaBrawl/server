package at.aau.hexabrawl.websocketserver.websocket.broker

class GameState(
    var players: MutableList<String> = mutableListOf(),
    var currentTurn: String? = null,
    var units: MutableList<GameUnit> = mutableListOf()
)