package at.aau.hexabrawl.websocketserver.service

import at.aau.hexabrawl.websocketserver.model.ErrorCode
import at.aau.hexabrawl.websocketserver.model.GameState

/** Ergebnis eines [EconomyService.buyUnit]-Aufrufs. */
sealed class BuyUnitResult {
    /** Einheit wurde erfolgreich platziert; [state] enthält den aktualisierten Spielzustand. */
    data class Placed(val state: GameState) : BuyUnitResult()
    /** Kauf wurde abgelehnt; [errorCode] und [message] beschreiben den Grund. */
    data class Rejected(val errorCode: ErrorCode, val message: String) : BuyUnitResult()
}
