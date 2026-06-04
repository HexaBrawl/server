package at.aau.hexabrawl.websocketserver.model

enum class ErrorCode {
    NOT_YOUR_TURN,
    INVALID_MOVE,
    GAME_FULL,
    GAME_NOT_STARTED,
    COLOR_ALREADY_TAKEN
}