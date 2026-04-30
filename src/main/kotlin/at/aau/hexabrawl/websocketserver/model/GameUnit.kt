package at.aau.hexabrawl.websocketserver.model

data class GameUnit(
    var player: String,
    var x: Int = 0,
    var y: Int = 0
)