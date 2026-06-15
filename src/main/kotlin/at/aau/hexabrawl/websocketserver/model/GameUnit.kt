package at.aau.hexabrawl.websocketserver.model

/**
 * Einzelne Spiel-Einheit auf dem Hex-Feld.
 *
 * [hasMovedThisTurn] verhindert, dass dieselbe Einheit pro Zug mehrfach
 * bewegt wird; das Flag wird beim Zugwechsel im
 * [at.aau.hexabrawl.websocketserver.service.TurnService.switchTurn] fuer
 * alle Einheiten zurueckgesetzt. Neu gekaufte Einheiten starten mit
 * `hasMovedThisTurn = true`, koennen also erst in der naechsten Runde
 * ziehen.
 */
data class GameUnit(
    var player: String,
    var x: Int = 0,
    var y: Int = 0,
    var type: UnitType,
    var hasMovedThisTurn: Boolean = false
)