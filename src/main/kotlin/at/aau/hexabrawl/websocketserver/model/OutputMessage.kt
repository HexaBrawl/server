package at.aau.hexabrawl.websocketserver.model

/**
 * Generisches Output-Frame der ursprünglichen WebSocket-Demo. Wird
 * nicht mehr von Production-Endpoints benutzt — Kandidat fuer Cleanup.
 */
data class OutputMessage(val from: String, val text: String, val time: String)