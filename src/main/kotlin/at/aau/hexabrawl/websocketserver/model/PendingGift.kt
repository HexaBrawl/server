package at.aau.hexabrawl.websocketserver.model

/**
 * Repraesentiert ein gerade laufendes Schummel-Geschenk.
 *
 * Wird in [GameState.pendingGift] gehalten, solange das Geschenk
 * "offen" ist. Beim ersten "Ja" eines Stealers wird das Delta
 * uebertragen und pendingGift auf null gesetzt.
 *
 * Property-Namen sind 1:1 identisch zum App-Repo
 * (data/serverside/PendingGift.kt) — die Jackson-Serialisierung soll
 * von beiden Seiten passen.
 *
 * - [ownerName]        Wer das Geschenk geoeffnet hat
 * - [delta]            Gold-Aenderung -10..+10 (vom Client gewuerfelt)
 * - [pendingDecisions] Wieviele Gegner noch nicht entschieden haben
 */
data class PendingGift(
    val ownerName: String,
    val delta: Int,
    val pendingDecisions: Int
)
