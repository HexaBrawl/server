package at.aau.hexabrawl.websocketserver.model

data class ErrorMessage(
    val errorCode: ErrorCode,
    val message: String
)
