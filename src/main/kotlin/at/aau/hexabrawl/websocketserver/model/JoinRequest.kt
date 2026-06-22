package at.aau.hexabrawl.websocketserver.model

/**
 * DTO für den Join-Request des Clients.
 *
 * Wird als JSON über STOMP an /app/rooms/{roomId}/join gesendet:
 *   { "name": "Alice", "color": "RED" }
 *
 * Spring/Jackson deserialisiert das automatisch in diese data class.
 *
 * Das Farbfeld ist nullable, damit Clients (und Tests) auch joinen
 * koennen, ohne eine Farbe vorzugeben. In diesem Fall vergibt
 * [at.aau.hexabrawl.websocketserver.service.PlayerService.handleJoin] die
 * Farbe dynamisch anhand der Spielerposition.
 */
data class JoinRequest(
    val name: String = "",
    val color: PlayerColor? = null
)
