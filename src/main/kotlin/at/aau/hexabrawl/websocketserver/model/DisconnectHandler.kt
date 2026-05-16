package at.aau.hexabrawl.websocketserver.model

import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component
import org.springframework.web.socket.messaging.SessionDisconnectEvent

@Component
class DisconnectHandler(
    private val gameService: GameService,
    private val messagingTemplate: SimpMessagingTemplate
) {

    @EventListener
    fun handleDisconnect(event: SessionDisconnectEvent) {
        val sessionId = event.sessionId
        println("DisconnectHandler: Session $sessionId disconnected")

        val playerExists = gameService.gameState.players.any { it.sessionId == sessionId }

        val updatedState = gameService.handleDisconnect(sessionId)

        if (playerExists) {
            messagingTemplate.convertAndSend("/topic/game", updatedState)
        }
    }
}