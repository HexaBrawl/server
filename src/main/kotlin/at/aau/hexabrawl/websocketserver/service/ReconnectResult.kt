package at.aau.hexabrawl.websocketserver.service

import at.aau.hexabrawl.websocketserver.model.ErrorCode
import at.aau.hexabrawl.websocketserver.model.GameState

/** Ergebnis eines [PlayerService.handleReconnect]-Aufrufs. */
sealed class ReconnectResult {
    /** Reconnect war erfolgreich; [state] enthält den aktualisierten Spielzustand. */
    data class Reconnected(val state: GameState) : ReconnectResult()
    /** Reconnect wurde abgelehnt; [errorCode] und [message] beschreiben den Grund. */
    data class Rejected(val errorCode: ErrorCode, val message: String) : ReconnectResult()
}
