package at.aau.hexabrawl.websocketserver.model


data class Player (
    val name : String = "",
    val sessionId : String = "",
    val color: PlayerColor = PlayerColor.RED,
    var gold: Int = 0,
    var farms: Int = 0,
    var income: Int = 0
){}