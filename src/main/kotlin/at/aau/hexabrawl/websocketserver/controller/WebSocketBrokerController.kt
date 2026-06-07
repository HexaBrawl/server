package at.aau.hexabrawl.websocketserver.controller

import at.aau.hexabrawl.websocketserver.model.ErrorCode
import at.aau.hexabrawl.websocketserver.model.ErrorMessage
import at.aau.hexabrawl.websocketserver.model.GameService
import at.aau.hexabrawl.websocketserver.model.GameState
import at.aau.hexabrawl.websocketserver.model.GameStatus
import at.aau.hexabrawl.websocketserver.model.JoinRequest
import at.aau.hexabrawl.websocketserver.model.Move
import at.aau.hexabrawl.websocketserver.model.PlayerColor
import at.aau.hexabrawl.websocketserver.model.RoomRegistry
import at.aau.hexabrawl.websocketserver.model.StompMessage
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Controller

@Controller
class WebSocketBrokerController(
    private val gameService: GameService,
    private val roomRegistry: RoomRegistry,
    private val messagingTemplate: SimpMessagingTemplate
) {
    companion object {
        private const val ROOM_NOT_FOUND_MESSAGE = "Raum nicht gefunden."
    }

    @MessageMapping("/hello")
    @SendTo("/topic/hello-response")
    fun handleHello(message: String): String {
        return "echo from broker: $message"
    }

    @MessageMapping("/object")
    @SendTo("/topic/rcv-object")
    fun handleObject(message: StompMessage): StompMessage {
        return message
    }

    /**
     * Broadcasts the current game state to all subscribers of the specified room.
     *
     * The state is sent to the room-specific STOMP topic
     * "/topic/rooms/{roomId}/state", ensuring that only clients subscribed
     * to this room receive the update.
     */
    private fun sendRoomState(roomId: String, state: GameState) {
        messagingTemplate.convertAndSend(
            "/topic/rooms/$roomId/state",
            state
        )
    }

    /**
     * Adds a player to the specified game room.
     *
     * Konsolidiert die Room-Architektur (#51) mit der Color-Auswahl aus
     * #107 und der Color-Konflikt-Erkennung aus #60/main:
     *  - Body ist [JoinRequest] (name + color), wie die App ihn schickt
     *  - Room-Existenz wird zuerst geprueft (ROOM_NOT_FOUND sonst)
     *  - Mode-Vollbelegung -> GAME_FULL
     *  - Farb-Konflikt -> COLOR_ALREADY_TAKEN (nur wenn Spieler neu ist;
     *    Re-Join mit gleichem Namen ist okay)
     *  - Erfolg: handleJoin mit color, dann Broadcast auf room-Topic
     */
    @MessageMapping("/rooms/{roomId}/join")
    fun joinRoom(
        @DestinationVariable roomId: String,
        request: JoinRequest,
        headerAccessor: SimpMessageHeaderAccessor
    ): GameState? {
        val sessionId = headerAccessor.sessionId ?: ""

        val room = roomRegistry.findById(roomId)
        if (room == null) {
            sendError(sessionId, ErrorCode.ROOM_NOT_FOUND, ROOM_NOT_FOUND_MESSAGE)
            return null
        }

        val currentState = gameService.getCurrentState(room.gameState)

        // Spiel voll?
        if (currentState.players.size >= room.mode.maxPlayers &&
            !currentState.players.any { it.name == request.name }
        ) {
            sendError(sessionId, ErrorCode.GAME_FULL, "Beitritt verweigert: Spiel ist voll.")
            return null
        }

        // Farb-Konflikt: nur fuer wirklich neue Spieler MIT explizit
        // gesetzter Farbe pruefen. Wenn der Client keine Farbe schickt,
        // vergibt handleJoin sie dynamisch und kann selbst keinen
        // Konflikt erzeugen.
        val requestedColor = request.color
        if (requestedColor != null &&
            !currentState.players.any { it.name == request.name } &&
            gameService.isColorTaken(room.gameState, requestedColor)
        ) {
            sendError(
                sessionId,
                ErrorCode.COLOR_ALREADY_TAKEN,
                "Beitritt verweigert: Farbe '$requestedColor' ist bereits vergeben."
            )
            return null
        }

        val state = gameService.handleJoin(
            room.gameState,
            request.name,
            sessionId,
            request.color
        )

        sendRoomState(roomId, state)
        return state
    }

    /**
     * Returns the current game state of the specified room.
     */
    @MessageMapping("/rooms/{roomId}/init")
    fun initRoom(
        @DestinationVariable roomId: String,
        headerAccessor: SimpMessageHeaderAccessor
    ): GameState? {
        val sessionId = headerAccessor.sessionId ?: ""

        val room = roomRegistry.findById(roomId)
        if (room == null) {
            sendError(sessionId, ErrorCode.ROOM_NOT_FOUND, ROOM_NOT_FOUND_MESSAGE)
            return null
        }

        val state = gameService.getCurrentState(room.gameState)
        sendRoomState(roomId, state)
        return state
    }

    /**
     * Executes a move in the specified game room.
     */
    @MessageMapping("/rooms/{roomId}/move")
    fun moveRoom(
        @DestinationVariable roomId: String,
        move: Move,
        headerAccessor: SimpMessageHeaderAccessor
    ): GameState? {
        val sessionId = headerAccessor.sessionId ?: ""

        val room = roomRegistry.findById(roomId)
        if (room == null) {
            sendError(sessionId, ErrorCode.ROOM_NOT_FOUND, ROOM_NOT_FOUND_MESSAGE)
            return null
        }

        val stateBefore = gameService.getCurrentState(room.gameState)

        if (stateBefore.status != GameStatus.IN_PROGRESS) {
            sendError(sessionId, ErrorCode.GAME_NOT_STARTED, "Zug abgelehnt: Spiel läuft nicht.")
            return null
        }

        if (move.player != stateBefore.currentTurn) {
            sendError(sessionId, ErrorCode.NOT_YOUR_TURN, "Es ist nicht dein Zug!")
            return null
        }

        val turnBefore = stateBefore.currentTurn
        val stateAfter = gameService.handleMove(room.gameState, move)

        if (stateAfter.currentTurn == turnBefore) {
            sendError(sessionId, ErrorCode.INVALID_MOVE, "Dieser Zug ist laut Regeln ungültig.")
            return null
        }

        sendRoomState(roomId, stateAfter)
        return stateAfter
    }

    /**
     * Kauft eine Farm fuer den Spieler im angegebenen Room.
     *
     * Portiert das `/buyFarm`-Mapping aus main (#60) in die room-scoped
     * Architektur (#51) und uebernimmt dabei auch die kebab-case-
     * Konvention, die die App erwartet (`/app/rooms/{id}/buy-farm`).
     *
     * Spieler wird ueber die sessionId aus dem STOMP-Header identifiziert
     * (sicherer als Payload-Trust). Bei zu wenig Gold: INSUFFICIENT_GOLD.
     *
     * Schlaegt damit Task #10 (Buy-Farm Room-Endpoint) gleich mit ab —
     * sonst waere die buyFarm-Logik aus main beim Merge ohne Mapping
     * im Codebase verwaist.
     */
    @MessageMapping("/rooms/{roomId}/buy-farm")
    fun buyFarmRoom(
        @DestinationVariable roomId: String,
        headerAccessor: SimpMessageHeaderAccessor
    ): GameState? {
        val sessionId = headerAccessor.sessionId ?: ""

        val room = roomRegistry.findById(roomId)
        if (room == null) {
            sendError(sessionId, ErrorCode.ROOM_NOT_FOUND, ROOM_NOT_FOUND_MESSAGE)
            return null
        }

        val state = room.gameState
        val player = state.players.find { it.sessionId == sessionId } ?: return null

        val cost = GameService.FARM_BASE_COST + (player.farms * GameService.FARM_COST_INCREMENT)
        if (player.gold < cost) {
            sendError(sessionId, ErrorCode.INSUFFICIENT_GOLD, "Nicht genug Gold für eine Farm!")
            return null
        }

        val updated = gameService.buyFarm(state, player.name)
        sendRoomState(roomId, updated)
        return updated
    }

    // Bridges fuer Tests, die noch ueber den globalen GameState gehen.
    fun handleJoin(name: String, sessionId: String, color: PlayerColor? = null) =
        gameService.handleJoin(name, sessionId, color)
    fun handleMove(move: Move) = gameService.handleMove(move)

    private fun sendError(user: String, code: ErrorCode, msg: String) {
        val errorResponse = ErrorMessage(code, msg)
        messagingTemplate.convertAndSendToUser(user, "/queue/errors", errorResponse)
    }
}
