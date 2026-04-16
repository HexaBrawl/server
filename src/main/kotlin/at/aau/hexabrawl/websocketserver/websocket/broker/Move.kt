package at.aau.hexabrawl.websocketserver.websocket.broker

data class Move(
    var player: String = "",
    var type: String = "",
    var fromX: Int = 0,
    var fromY: Int = 0,
    var toX: Int = 0,
    var toY: Int = 0
)