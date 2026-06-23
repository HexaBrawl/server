package at.aau.hexabrawl.websocketserver.config

import at.aau.hexabrawl.websocketserver.websocket.WebSocketHandlerImpl
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

/**
 * Registriert den Plain-WebSocket-Handler (`/websocket-example-handler`)
 * neben dem STOMP-Broker. Wird im Production-Flow von der App nicht
 * genutzt — existiert als Demo/Smoke-Test-Endpoint.
 */
@Configuration
@EnableWebSocket
class WebSocketHandlerConfig : WebSocketConfigurer {
    /**
     * Registriert den [at.aau.hexabrawl.websocketserver.websocket.WebSocketHandlerImpl]
     * am Endpunkt `/websocket-example-handler` mit offenem CORS-Ursprung.
     */
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(WebSocketHandlerImpl(), "/websocket-example-handler")
            .setAllowedOrigins("*")
    }
}