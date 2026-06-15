package at.aau.hexabrawl.websocketserver.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

/**
 * Spring-Konfiguration des STOMP-Brokers ueber WebSocket.
 *
 *  - STOMP-Endpunkt: `/websocket-example-broker` (von der App so genutzt).
 *  - Broker-Destinations: `/topic` (Broadcast) und `/queue` (per-User).
 *  - App-Prefix: `/app` — vom Client gesendete Frames landen auf
 *    `@MessageMapping`-Methoden der Controller.
 *  - Heartbeat 20s/20s gegen Azure-Idle-Timeout, gestueztt durch einen
 *    eigenen Task-Scheduler.
 */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketBrokerConfig : WebSocketMessageBrokerConfigurer {

    override fun configureMessageBroker(config: MessageBrokerRegistry) {
        config.enableSimpleBroker("/topic", "/queue")
            // STOMP-Heartbeats: alle 20s in beide Richtungen. Verhindert
            // Azure-Idle-Timeout-bedingte WebSocket-Disconnects.
            .setHeartbeatValue(longArrayOf(20_000, 20_000))
            .setTaskScheduler(taskScheduler())
        config.setApplicationDestinationPrefixes("/app")
        config.setUserDestinationPrefix("/user")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/websocket-example-broker")
            .setAllowedOrigins("*")
    }

    @Bean
    fun taskScheduler(): ThreadPoolTaskScheduler =
        ThreadPoolTaskScheduler().apply {
            setPoolSize(1)
            setThreadNamePrefix("ws-heartbeat-")
            initialize()
        }
}