package at.aau.hexabrawl.websocketserver.model

/**
 * Request-DTO fuer /app/rooms/{roomId}/cheat/claim-gift.
 *
 * Property-Namen sind 1:1 identisch zum App-Repo
 * (UnitMoveEndpoint.claimCheatGift) — Jackson-Serialisierung
 * soll von beiden Seiten passen.
 *
 * - [playerName] Wer das Geschenk oeffnet
 * - [delta]      Vom Client gewuerfeltes Delta -10..+10
 */
data class ClaimGiftRequest(
    val playerName: String = "",
    val delta: Int = 0
)
