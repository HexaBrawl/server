package at.aau.hexabrawl.websocketserver.controller

import at.aau.hexabrawl.websocketserver.model.GameMode
import at.aau.hexabrawl.websocketserver.model.GameState
import at.aau.hexabrawl.websocketserver.model.RoomRegistry
import at.aau.hexabrawl.websocketserver.websocket.StompFrameHandlerClientImpl
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.messaging.converter.JacksonJsonMessageConverter
import org.springframework.messaging.converter.MessageConverter
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

/**
 * Smoke-Tests fuer die Spring-STOMP-Pipeline.
 *
 * Prueft, dass Spring Boot mit der WebSocket-Broker-Konfiguration sauber
 * hochkommt, ein STOMP-Client sich verbinden kann, eine @MessageMapping-
 * Methode antwortet und der Broadcast den Subscriber erreicht.
 *
 * Genutzt wird dafuer der /rooms/{roomId}/init-Endpoint (LobbyController),
 * der bei jeder Anfrage idempotent den aktuellen GameState an
 * /topic/rooms/{roomId}/state broadcastet.
 */
@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketBrokerIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var roomRegistry: RoomRegistry

    @Test
    fun `init broadcasts current GameState to subscribers`() {
        val room = roomRegistry.createRoom(GameMode.DUAL_VALLEY)
        val messages: BlockingQueue<GameState> = LinkedBlockingDeque()
        val session = initStompSession(
            "/topic/rooms/${room.roomId}/state",
            JacksonJsonMessageConverter(),
            messages,
            GameState::class.java
        )

        // /init triggert einen Broadcast des aktuellen State an alle Subscriber
        session.send("/app/rooms/${room.roomId}/init", "{}")

        val received = messages.poll(3, TimeUnit.SECONDS)
        Assertions.assertThat(received).isNotNull
        Assertions.assertThat(received.players).isEmpty()
    }

    /**
     * @return The Stomp session for the WebSocket connection (Stomp - WebSocket is comparable to HTTP - TCP).
     */
    private fun <T> initStompSession(
        destination: String,
        messageConverter: MessageConverter,
        queue: BlockingQueue<T>,
        expectedType: Class<T>
    ): StompSession {
        val stompClient = WebSocketStompClient(StandardWebSocketClient())
        stompClient.messageConverter = messageConverter

        // connect client to the websocket server (using Kotlin String interpolation for the port)
        val websocketUri = "ws://localhost:$port/websocket-example-broker"
        val session = stompClient.connectAsync(websocketUri, object : StompSessionHandlerAdapter() {})
            .get(10, TimeUnit.SECONDS) // wait up to 10 sec for the client to be connected

        // subscribes to the topic
        session.subscribe(destination, StompFrameHandlerClientImpl(queue, expectedType))

        return session
    }
}
