package at.aau.hexabrawl.websocketserver.service

import at.aau.hexabrawl.websocketserver.model.ErrorCode
import at.aau.hexabrawl.websocketserver.model.GameState

sealed class ClaimGiftResult {
    data class Claimed(val state: GameState) : ClaimGiftResult()
    data class Rejected(val errorCode: ErrorCode, val message: String) : ClaimGiftResult()
}
