package at.aau.hexabrawl.websocketserver.model


data class Player (
    val name : String = "",
    var sessionId : String = "",
    val color: PlayerColor = PlayerColor.RED,
    var gold: Int = 0,
    var farms: Int = 0,
    var income: Int = 0,
    var upkeep: Int = 0,
    var hasUsedGift: Boolean = false,
    var connected: Boolean = true,
    var disconnectedAt: Long? = null
){}