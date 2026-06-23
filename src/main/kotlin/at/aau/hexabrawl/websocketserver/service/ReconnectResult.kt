package at.aau.hexabrawl.websocketserver.service

import at.aau.hexabrawl.websocketserver.model.ErrorCode
import at.aau.hexabrawl.websocketserver.model.GameState

sealed class ReconnectResult {
    data class Reconnected(val state: GameState) : ReconnectResult()
    data class Rejected(val errorCode: ErrorCode, val message: String) : ReconnectResult()
}
