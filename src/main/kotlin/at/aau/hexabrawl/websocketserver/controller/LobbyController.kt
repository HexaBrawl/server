package at.aau.hexabrawl.websocketserver.controller

import at.aau.hexabrawl.websocketserver.model.ErrorCode
import at.aau.hexabrawl.websocketserver.model.GameState
import at.aau.hexabrawl.websocketserver.model.JoinRequest
import at.aau.hexabrawl.websocketserver.model.LeaveRequest
import at.aau.hexabrawl.websocketserver.model.ReconnectRequest
import at.aau.hexabrawl.websocketserver.service.EconomyService
import at.aau.hexabrawl.websocketserver.service.PlayerService
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Controller

/**
 * Endpoints rund um den Room-Lifecycle eines Spielers:
 *  - /rooms/{roomId}/join       Spieler tritt einem Raum bei
 *  - /rooms/{roomId}/init       Aktuellen GameState anfordern
 *  - /rooms/{roomId}/reconnect  Wartender Spieler kehrt nach Drop zurueck
 *  - /rooms/{roomId}/leave      Explizites Verlassen ohne Grace Period
 */
@Controller
class LobbyController(
    private val playerService: PlayerService,
    private val economyService: EconomyService,
    private val contextResolver: GameContextResolver,
    private val messagingTemplate: SimpMessagingTemplate
) {

    private fun sendRoomState(roomId: String, state: GameState) {
        economyService.recomputePlayerStats(state)
        messagingTemplate.convertAndSend("/topic/rooms/${roomId}/state", state)
    }

    @MessageMapping("/rooms/{roomId}/join")
    fun joinRoom(
        @DestinationVariable roomId: String,
        request: JoinRequest,
        headerAccessor: SimpMessageHeaderAccessor
    ): GameState? {
        val sessionId = headerAccessor.sessionId ?: ""

        val ctx = contextResolver.resolveRoom(sessionId, roomId) ?: return null
        val currentState = ctx.room.gameState

        if (currentState.players.size >= ctx.room.mode.maxPlayers &&
            !currentState.players.any { it.name == request.name }
        ) {
            contextResolver.sendError(sessionId, ErrorCode.GAME_FULL, "Beitritt verweigert: Spiel ist voll.")
            return null
        }

        val requestedColor = request.color
        if (requestedColor != null &&
            !currentState.players.any { it.name == request.name } &&
            playerService.isColorTaken(currentState, requestedColor)
        ) {
            contextResolver.sendError(
                sessionId,
                ErrorCode.COLOR_ALREADY_TAKEN,
                "Beitritt verweigert: Farbe '$requestedColor' ist bereits vergeben."
            )
            return null
        }

        val state = playerService.handleJoin(
            currentState,
            request.name,
            sessionId,
            request.color
        )

        sendRoomState(roomId, state)
        return state
    }

    @MessageMapping("/rooms/{roomId}/init")
    fun initRoom(
        @DestinationVariable roomId: String,
        headerAccessor: SimpMessageHeaderAccessor
    ): GameState? {
        val sessionId = headerAccessor.sessionId ?: ""

        val ctx = contextResolver.resolveRoom(sessionId, roomId) ?: return null
        val state = ctx.state
        sendRoomState(roomId, state)
        return state
    }

    /**
     * Reconnect: wartender Spieler kommt nach Grace Period zurueck.
     *
     * Identifikation via playerName + joinCode (Memory-Persistenz im Client).
     * Bei Erfolg wird connected = true, disconnectedAt = null und die neue
     * sessionId an den Player gebunden, sodass weitere Messages korrekt
     * ankommen.
     */
    @MessageMapping("/rooms/{roomId}/reconnect")
    fun reconnectRoom(
        @DestinationVariable roomId: String,
        request: ReconnectRequest,
        headerAccessor: SimpMessageHeaderAccessor
    ): GameState? {
        val sessionId = headerAccessor.sessionId ?: ""

        val ctx = contextResolver.resolveRoom(sessionId, roomId) ?: return null

        if (ctx.room.joinCode != request.joinCode) {
            contextResolver.sendError(sessionId, ErrorCode.RECONNECT_REJECTED, "JoinCode passt nicht.")
            return null
        }

        val state = ctx.state
        val player = state.players.find { it.name == request.playerName }
        if (player == null || player.connected) {
            contextResolver.sendError(sessionId, ErrorCode.RECONNECT_REJECTED, "Kein wartender Spieler mit diesem Namen.")
            return null
        }

        synchronized(state.lock) {
            player.connected = true
            player.disconnectedAt = null
            player.sessionId = sessionId
        }
        sendRoomState(roomId, state)
        return state
    }

    /**
     * Explizites Spiel-Verlassen — umgeht die 30s Grace Period.
     *
     * Wird vom Client aufgerufen wenn der User bewusst rausgeht
     * ("Spiel verlassen"-Button oder Activity.onDestroy mit isFinishing).
     */
    @MessageMapping("/rooms/{roomId}/leave")
    fun leaveRoom(
        @DestinationVariable roomId: String,
        request: LeaveRequest,
        headerAccessor: SimpMessageHeaderAccessor
    ): GameState? {
        val sessionId = headerAccessor.sessionId ?: ""

        val ctx = contextResolver.resolveRoom(sessionId, roomId) ?: return null
        val state = ctx.state

        val player = state.players.find {
            it.name == request.playerName && it.sessionId == sessionId
        } ?: return null

        playerService.hardDelete(state, player)
        sendRoomState(roomId, state)
        return state
    }
}
