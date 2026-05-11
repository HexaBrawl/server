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

        val updatedState = gameService.handleDisconnect(sessionId)

        // Broadcast an alle Spieler
        messagingTemplate.convertAndSend("/topic/game", updatedState)
    }
}