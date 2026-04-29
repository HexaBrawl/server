package at.aau.hexabrawl.websocketserver.model

data class Move(
    var player: String = "",
    var type: String = "",
    var fromX: Int = 0,
    var fromY: Int = 0,
    var toX: Int = 0,
    var toY: Int = 0
)