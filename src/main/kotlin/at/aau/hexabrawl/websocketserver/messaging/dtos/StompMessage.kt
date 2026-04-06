package at.aau.hexabrawl.websocketserver.messaging.dtos

data class StompMessage(
    val from: String,
    val text: String
)

