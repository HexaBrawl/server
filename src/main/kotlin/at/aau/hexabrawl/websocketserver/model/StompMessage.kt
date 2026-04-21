package at.aau.hexabrawl.websocketserver.model

data class StompMessage(
    val from: String,
    val text: String
)