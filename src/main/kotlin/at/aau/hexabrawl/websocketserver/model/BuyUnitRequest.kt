package at.aau.hexabrawl.websocketserver.model

/**
 * DTO fuer Buy-Unit-Requests (#132).
 *
 * Wird vom Client als JSON ueber STOMP gesendet:
 *   { "playerName": "Alice", "type": "INFANTRY", "x": 3, "y": 4 }
 *
 * Defaults sind reine Jackson-Krueke (no-arg-Konstruktor fuer
 * Deserialisierung); der Client schickt immer alle Felder.
 */
data class BuyUnitRequest(
    val playerName: String = "",
    val type: UnitType = UnitType.INFANTRY,
    val x: Int = 0,
    val y: Int = 0
)