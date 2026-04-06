package at.aau.hexabrawl.websocketserver.websocket.handler

import org.springframework.web.socket.*

class WebSocketHandlerImpl : WebSocketHandler {

    override fun afterConnectionEstablished(session: WebSocketSession) {
    }

    override fun handleMessage(session: WebSocketSession, message: WebSocketMessage<*>) {
        // TODO handle the messages here
        session.sendMessage(TextMessage("echo from handler: ${message.payload}"))
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
    }

    override fun afterConnectionClosed(session: WebSocketSession, closeStatus: CloseStatus) {
    }

    override fun supportsPartialMessages(): Boolean {
        return false
    }
}