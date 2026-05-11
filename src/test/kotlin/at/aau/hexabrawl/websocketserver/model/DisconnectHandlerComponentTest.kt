package at.aau.hexabrawl.websocketserver.model

import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.web.socket.messaging.SessionDisconnectEvent

class DisconnectHandlerComponentTest {

    private val gameService = GameService()
    private val messagingTemplate = Mockito.mock(SimpMessagingTemplate::class.java)
    private val handler = DisconnectHandler(gameService, messagingTemplate)

    @Test
    fun `handleDisconnect broadcasts updated state`() {
        gameService.handleJoin("Alice", "session-1")
        gameService.handleJoin("Bob", "session-2")

        val event = Mockito.mock(SessionDisconnectEvent::class.java)
        Mockito.`when`(event.sessionId).thenReturn("session-1")

        handler.handleDisconnect(event)

        Mockito.verify(messagingTemplate).convertAndSend(
            ArgumentMatchers.eq("/topic/game"),
            ArgumentMatchers.any(GameState::class.java)
        )
    }

    @Test
    fun `handleDisconnect removes player from game`() {
        gameService.handleJoin("Alice", "session-1")

        val event = Mockito.mock(SessionDisconnectEvent::class.java)
        Mockito.`when`(event.sessionId).thenReturn("session-1")

        handler.handleDisconnect(event)

        assert(gameService.gameState.players.isEmpty())
    }
}