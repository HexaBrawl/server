package at.aau.hexabrawl.websocketserver.model

/**
 * Repraesentiert ein einzelnes Hex-Feld auf dem Spielbrett.
 *
 * Felder gehoeren entweder einem Spieler (durch Eroberung oder als
 * Startgebiet) oder sind neutral (owner = null). Die Position (x, y)
 * verwendet die gleichen "odd-q offset" Koordinaten wie die Einheiten
 * im GameState.
 *
 * `owner` ist mutable damit Felder durch Eroberung den Besitzer
 * wechseln koennen (siehe Folge-Sub-Issue #104).
 */
data class Field(
    val x: Int,
    val y: Int,
    var owner: String? = null
)
