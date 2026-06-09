package at.aau.hexabrawl.websocketserver.model

/**
 * DTO für den End-Turn-Request des Clients.
 *
 * Wird als JSON ueber STOMP an /app/rooms/{roomId}/end-turn gesendet:
 *   { "playerName": "Alice" }
 *
 * Spring/Jackson deserialisiert das automatisch in diese data class.
 * Default ist Gson-Krueke fuer die Reflection-basierte Deserialisierung.
 */
data class EndTurnRequest(
    val playerName: String = ""
)
