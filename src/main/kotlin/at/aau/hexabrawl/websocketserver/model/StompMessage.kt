package at.aau.hexabrawl.websocketserver.model

/**
 * Wire-Format der ursprünglichen Echo-Demo (/app/object). Wird nur noch
 * von einem alten Coverage-Test referenziert — Kandidat fuer Cleanup.
 */
data class StompMessage(
    val from: String,
    val text: String
)