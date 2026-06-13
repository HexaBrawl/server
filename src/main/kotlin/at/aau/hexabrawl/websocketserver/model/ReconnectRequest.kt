package at.aau.hexabrawl.websocketserver.model

/**
 * Request-DTO fuer /app/rooms/{roomId}/reconnect.
 *
 * Wird vom Client geschickt, nachdem die WebSocket-Verbindung neu aufgebaut
 * wurde. Identifikation des wartenden Spielers via [playerName] + [joinCode].
 *
 * Property-Namen sollten 1:1 zum App-Repo passen, damit Jackson-Serialisierung
 * beidseitig funktioniert.
 */
data class ReconnectRequest(
    val playerName: String = "",
    val joinCode: String = ""
)
