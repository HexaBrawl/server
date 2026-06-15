package at.aau.hexabrawl.websocketserver.model

/**
 * Wire-Format der Fehler-Frames, die der Server an /user/queue/errors
 * sendet. [errorCode] dient dem Client zur sprach-/UI-unabhaengigen
 * Behandlung, [message] ist ein lokalisierter Anzeige-Text.
 */
data class ErrorMessage(
    val errorCode: ErrorCode,
    val message: String
)
