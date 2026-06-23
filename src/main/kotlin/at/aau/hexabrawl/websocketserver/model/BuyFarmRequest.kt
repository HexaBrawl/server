package at.aau.hexabrawl.websocketserver.model

/**
 * DTO fuer Buy-Farm-Requests.
 *
 * Wird vom Client als JSON ueber STOMP gesendet:
 *   { "playerName": "Alice" }
 *
 * Default ist reine Jackson-Krueke (no-arg-Konstruktor fuer
 * Deserialisierung); der Client schickt immer alle Felder.
 */
data class BuyFarmRequest(
    val playerName: String = ""
)
