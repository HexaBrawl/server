package at.aau.hexabrawl.websocketserver.model

/**
 * Request-DTO fuer /app/rooms/{roomId}/cheat/respond-steal.
 *
 * Property-Namen sind 1:1 identisch zum App-Repo
 * (UnitMoveEndpoint.respondToCheatGift).
 *
 * - [playerName] Wer auf das Steal-Popup antwortet
 * - [accept]     true = "Ja klauen", false = "Nein, lass ihm das Gold"
 */
data class StealResponseRequest(
    val playerName: String = "",
    val accept: Boolean = false
)
