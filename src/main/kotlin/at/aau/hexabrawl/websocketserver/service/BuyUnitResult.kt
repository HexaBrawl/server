package at.aau.hexabrawl.websocketserver.service

import at.aau.hexabrawl.websocketserver.model.ErrorCode
import at.aau.hexabrawl.websocketserver.model.GameState

sealed class BuyUnitResult {
    data class Placed(val state: GameState) : BuyUnitResult()
    data class Rejected(val errorCode: ErrorCode, val message: String) : BuyUnitResult()
}
