package at.aau.hexabrawl.websocketserver.service

import at.aau.hexabrawl.websocketserver.model.ErrorCode
import at.aau.hexabrawl.websocketserver.model.GameState

/** Ergebnis eines [CheatGiftService.respondCheatSteal]-Aufrufs. */
sealed class StealResult {
    /** Entscheidung wurde verarbeitet; [state] enthält den aktualisierten Spielzustand. */
    data class Resolved(val state: GameState) : StealResult()
    /** Aktion wurde abgelehnt; [errorCode] und [message] beschreiben den Grund. */
    data class Rejected(val errorCode: ErrorCode, val message: String) : StealResult()
}
