package at.aau.hexabrawl.websocketserver.service

import at.aau.hexabrawl.websocketserver.model.ErrorCode
import at.aau.hexabrawl.websocketserver.model.GameState

/** Ergebnis eines [CheatGiftService.claimCheatGift]-Aufrufs. */
sealed class ClaimGiftResult {
    /** Geschenk wurde erfolgreich beansprucht; [state] enthält den aktualisierten Spielzustand. */
    data class Claimed(val state: GameState) : ClaimGiftResult()
    /** Aktion wurde abgelehnt; [errorCode] und [message] beschreiben den Grund. */
    data class Rejected(val errorCode: ErrorCode, val message: String) : ClaimGiftResult()
}
