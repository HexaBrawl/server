package at.aau.hexabrawl.websocketserver.websocket

import org.springframework.web.socket.*

/**
 * Plain-WebSocket-Echo-Handler aus der ursprünglichen Demo. Wird ueber
 * [at.aau.hexabrawl.websocketserver.config.WebSocketHandlerConfig] am
 * Endpoint `/websocket-example-handler` registriert; im Production-Flow
 * der App nicht in Verwendung.
 */
class WebSocketHandlerImpl : WebSocketHandler {

    /** Wird aufgerufen, sobald eine neue WebSocket-Verbindung aufgebaut wurde. */
    override fun afterConnectionEstablished(session: WebSocketSession) {
    }

    /**
     * Verarbeitet eingehende WebSocket-Nachrichten und sendet sie als Echo zurück.
     *
     * @param session Die aktive WebSocket-Session.
     * @param message Die empfangene Nachricht.
     */
    override fun handleMessage(session: WebSocketSession, message: WebSocketMessage<*>) {
        // TODO handle the messages here
        session.sendMessage(TextMessage("echo from handler: ${message.payload}"))
    }

    /**
     * Wird bei einem Transport-Fehler aufgerufen.
     *
     * @param session   Die betroffene WebSocket-Session.
     * @param exception Der aufgetretene Fehler.
     */
    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
    }

    /**
     * Wird aufgerufen, nachdem eine WebSocket-Verbindung geschlossen wurde.
     *
     * @param session     Die geschlossene WebSocket-Session.
     * @param closeStatus Der Schließ-Status mit Code und Grund.
     */
    override fun afterConnectionClosed(session: WebSocketSession, closeStatus: CloseStatus) {
    }

    /**
     * Gibt an, ob dieser Handler partielle Nachrichten unterstützt.
     * @return false — partielle Nachrichten werden nicht unterstützt.
     */
    override fun supportsPartialMessages(): Boolean {
        return false
    }
}
