package at.aau.hexabrawl.websocketserver.model

import at.aau.hexabrawl.websocketserver.service.GameService
import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component
import org.springframework.web.socket.messaging.SessionDisconnectEvent

/**
 * Handles WebSocket disconnect events.
 *
 * When a client disconnects, the handler locates the affected room,
 * removes the corresponding player from that room and broadcasts the
 * updated game state to subscribers of the room-specific topic.
 */
@Component
class DisconnectHandler(
    private val gameService: GameService,
    private val roomRegistry: RoomRegistry,
    private val messagingTemplate: SimpMessagingTemplate
) {

    /**
     * Processes a WebSocket disconnect event.
     *
     * The disconnected player is removed only from the room they belong to.
     * The updated room state is then broadcast to all subscribers of the
     * corresponding room topic.
     *
     * @param event The Spring WebSocket disconnect event.
     */
    @EventListener
    fun handleDisconnect(event: SessionDisconnectEvent) {
        val sessionId = event.sessionId
        println("DisconnectHandler: Session $sessionId disconnected")

        val room = roomRegistry.getAllRooms()
            .find { room ->
                room.gameState.players.any {
                    it.sessionId == sessionId
                }
            } ?: return

        val updatedState = gameService.handleDisconnect(room.gameState, sessionId)

        messagingTemplate.convertAndSend("/topic/rooms/${room.roomId}/state",
            updatedState)
    }
}