package at.aau.hexabrawl.websocketserver

import at.aau.hexabrawl.websocketserver.messaging.dtos.StompMessage
import at.aau.hexabrawl.websocketserver.websocket.StompFrameHandlerClientImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.messaging.converter.JacksonJsonMessageConverter
import org.springframework.messaging.converter.MessageConverter
import org.springframework.messaging.converter.StringMessageConverter
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketBrokerIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    private val WEBSOCKET_TOPIC = "/topic/hello-response"
    private val WEBSOCKET_TOPIC_OBJECT = "/topic/rcv-object"

    @Test
    fun testWebSocketMessageBroker() {
        val messages: BlockingQueue<String> = LinkedBlockingDeque()
        val session = initStompSession(WEBSOCKET_TOPIC, StringMessageConverter(), messages, String::class.java)

        // send a message to the server
        val message = "Test message"
        session.send("/app/hello", message)

        val expectedResponse = "echo from broker: $message"
        assertThat(messages.poll(1, TimeUnit.SECONDS)).isEqualTo(expectedResponse)
    }

    @Test
    fun testWebSocketMessageBrokerHandleObject() {
        val messages: BlockingQueue<StompMessage> = LinkedBlockingDeque()
        val session = initStompSession(WEBSOCKET_TOPIC_OBJECT, JacksonJsonMessageConverter(), messages, StompMessage::class.java)

        // send a message object to the server
        val message = StompMessage("client", "Test Object Message")
        session.send("/app/object", message)

        assertThat(messages.poll(1, TimeUnit.SECONDS)).isEqualTo(message)
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
            .get(1, TimeUnit.SECONDS) // wait 1 sec for the client to be connected

        // subscribes to the topic defined in WebSocketBrokerController
        session.subscribe(destination, StompFrameHandlerClientImpl(queue, expectedType))

        return session
    }
}