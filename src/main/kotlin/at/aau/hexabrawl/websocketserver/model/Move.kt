package at.aau.hexabrawl.websocketserver.model

/**
 * Move-Request vom Client an den /rooms/{roomId}/move-Endpoint.
 *
 * Identifiziert die zu bewegende Einheit ueber [player], [type] und
 * Start-Koordinaten ([fromX], [fromY]); Zielfeld ist ([toX], [toY]).
 * Validierung (Distanz <= 2 Hex, eigene/Nachbar-Felder, Combat etc.)
 * passiert in
 * [at.aau.hexabrawl.websocketserver.service.TurnService.handleMove].
 */
data class Move(
    var player: String = "",
    var type: UnitType = UnitType.INFANTRY,
    var fromX: Int = 0,
    var fromY: Int = 0,
    var toX: Int = 0,
    var toY: Int = 0
)