package at.aau.hexabrawl.websocketserver.websocket

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.WebSocketClient
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketHandlerIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    /**
     * Queue of messages from the server.
     */
    private val messages: BlockingQueue<String> = LinkedBlockingDeque()

    @Test
    fun testWebSocketMessageBroker() {
        val session = initWebSocketSession()

        // send a message to the server
        val message = "Test message"
        session.sendMessage(TextMessage(message))

        val expectedResponse = "echo from handler: $message"
        Assertions.assertThat(messages.poll(1, TimeUnit.SECONDS)).isEqualTo(expectedResponse)
    }

    /**
     * @return The basic session for the WebSocket connection.
     */
    private fun initWebSocketSession(): WebSocketSession {
        val client: WebSocketClient = StandardWebSocketClient()

        // connect client to the websocket server (using Kotlin String interpolation for the port)
        val websocketUri = "ws://localhost:$port/websocket-example-handler"

        val session = client.execute(WebSocketHandlerClientImpl(messages), websocketUri)
                .get(1, TimeUnit.SECONDS) // wait 1 sec for the client to be connected

        return session
    }
}