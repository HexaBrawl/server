package at.aau.hexabrawl.websocketserver.model

/**
 * DTO für den Join-Request des Clients.
 *
 * Wird als JSON über STOMP an /app/join gesendet:
 *   { "name": "Alice", "color": "RED" }
 *
 * Spring/Jackson deserialisiert das automatisch in diese data class.
 */
data class JoinRequest(
    val name: String = "",
    val color: PlayerColor = PlayerColor.RED
)
