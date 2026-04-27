package at.aau.hexabrawl.websocketserver.model

data class GameUnit(
    var player: Player,
    var x: Int = 0,
    var y: Int = 0
)