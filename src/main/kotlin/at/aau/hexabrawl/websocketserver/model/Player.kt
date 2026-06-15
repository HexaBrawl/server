package at.aau.hexabrawl.websocketserver.model


/**
 * Spieler-Zustand innerhalb eines Raums.
 *
 * Verbindungs-Lifecycle: [connected] und [disconnectedAt] gehoeren
 * zusammen — beim Soft-Disconnect wird `connected=false` UND
 * `disconnectedAt=System.currentTimeMillis()` gesetzt, beim Reconnect
 * beides zurueck. Nach 30s ohne Reconnect erfolgt Hard-Delete via
 * [at.aau.hexabrawl.websocketserver.service.DisconnectCleanupService].
 *
 * Wirtschaft: [gold] / [farms] werden vom EconomyService gepflegt;
 * [income] und [upkeep] sind abgeleitete Werte fuers UI.
 */
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