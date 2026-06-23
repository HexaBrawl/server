package at.aau.hexabrawl.websocketserver.service

import at.aau.hexabrawl.websocketserver.model.ErrorCode
import at.aau.hexabrawl.websocketserver.model.GameState

sealed class StealResult {
    data class Resolved(val state: GameState) : StealResult()
    data class Rejected(val errorCode: ErrorCode, val message: String) : StealResult()
}
