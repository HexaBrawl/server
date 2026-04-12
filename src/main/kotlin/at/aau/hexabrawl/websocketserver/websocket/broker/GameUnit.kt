package at.aau.hexabrawl.websocketserver.websocket.broker

data class GameUnit(
    var player: String = "",
    var x: Int = 0,
    var y: Int = 0
)