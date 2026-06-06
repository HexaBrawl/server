package at.aau.hexabrawl.websocketserver.controller

import at.aau.hexabrawl.websocketserver.model.ErrorCode
import at.aau.hexabrawl.websocketserver.model.ErrorMessage
import at.aau.hexabrawl.websocketserver.model.GameService
import at.aau.hexabrawl.websocketserver.model.GameState
import at.aau.hexabrawl.websocketserver.model.GameStatus
import at.aau.hexabrawl.websocketserver.model.Move
import at.aau.hexabrawl.websocketserver.model.RoomRegistry
import at.aau.hexabrawl.websocketserver.model.StompMessage
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.messaging.simp.annotation.SendToUser

@Controller
class WebSocketBrokerController(
    private val gameService: GameService,
    private val roomRegistry: RoomRegistry,
    private val messagingTemplate: SimpMessagingTemplate
) {
    companion object {
        private const val ROOM_NOT_FOUND_MESSAGE =
            "Raum nicht gefunden."
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

    @MessageMapping("/join")
    @SendTo("/topic/game")
    fun join(
        playerName: String,
        headerAccessor: SimpMessageHeaderAccessor
    ): GameState? {

        val sessionId = headerAccessor.sessionId ?: ""
        val currentState = gameService.getCurrentState()

        if (currentState.players.size >= GameService.MAX_PLAYERS &&
            !currentState.players.any { it.name == playerName }) {
            sendError(sessionId, ErrorCode.GAME_FULL, "Beitritt verweigert: Spiel ist voll.")
            return null
        }

        return gameService.handleJoin(playerName, sessionId)
    }

    /**
     * Broadcasts the current game state to all subscribers of the specified room.
     *
     * The state is sent to the room-specific STOMP topic
     * "/topic/rooms/{roomId}/state", ensuring that only clients subscribed
     * to this room receive the update.
     *
     * @param roomId Unique identifier of the room whose state is broadcast.
     * @param state Current game state of the room.
     */
    private fun sendRoomState(
        roomId: String,
        state: GameState
    ) {
        messagingTemplate.convertAndSend(
            "/topic/rooms/$roomId/state",
            state
        )
    }

    /**
     * Adds a player to the specified game room.
     *
     * The room is identified by its unique roomId. If the room exists and is not
     * full, the player is added to the room's game state and the updated state is
     * broadcast to all subscribers of the room-specific topic.
     *
     * If the room does not exist, a ROOM_NOT_FOUND error is sent to the client.
     *
     * @param roomId Unique identifier of the target room.
     * @param playerName Name of the player joining the room.
     * @param headerAccessor Provides access to the STOMP session information.
     * @return Updated GameState if the player was successfully added; null otherwise.
     */
    @MessageMapping("/rooms/{roomId}/join")
    fun joinRoom(
        @DestinationVariable roomId: String,
        playerName: String,
        headerAccessor: SimpMessageHeaderAccessor
    ): GameState? {

        val room = roomRegistry.findById(roomId)

        if (room == null) {
            sendError(
                headerAccessor.sessionId ?: "",
                ErrorCode.ROOM_NOT_FOUND,
                ROOM_NOT_FOUND_MESSAGE
            )
            return null
        }

        val sessionId = headerAccessor.sessionId ?: ""

        val currentState = gameService.getCurrentState(room.gameState)

        if (currentState.players.size >= room.mode.maxPlayers &&
            !currentState.players.any { it.name == playerName }) {

            sendError(
                sessionId,
                ErrorCode.GAME_FULL,
                "Beitritt verweigert: Spiel ist voll."
            )

            return null
        }

        val state = gameService.handleJoin(
            room.gameState,
            playerName,
            sessionId
        )

        sendRoomState(
            roomId,
            state
        )

        return state
    }


    /**
     * Returns the current game state of the specified room.
     *
     * The room is identified by its unique roomId. The current state is
     * broadcast to all subscribers of the room-specific topic and returned
     * to the requesting client.
     *
     * If the room does not exist, a ROOM_NOT_FOUND error is sent to the client.
     *
     * @param roomId Unique identifier of the requested room.
     * @param headerAccessor Provides access to the STOMP session information.
     * @return Current GameState of the room, or null if the room does not exist.
     */
    @MessageMapping("/rooms/{roomId}/init")
    fun initRoom(
        @DestinationVariable roomId: String,
        headerAccessor: SimpMessageHeaderAccessor
    ): GameState? {

        val sessionId = headerAccessor.sessionId ?: ""
        val room = roomRegistry.findById(roomId)
        if (room == null) {
            sendError(
                sessionId,
                ErrorCode.ROOM_NOT_FOUND,
                ROOM_NOT_FOUND_MESSAGE
            )
            return null
        }

        val state = gameService.getCurrentState(
            room.gameState
        )

        sendRoomState(
            roomId,
            state
        )

        return state
    }

     /**
     * Executes a move in the specified game room.
     *
     * The room is identified by its unique roomId. The move is validated against
     * the room's current game state. If the move is valid, the updated game state
     * is broadcast to all subscribers of the room-specific topic.
     *
     * If the room does not exist, a ROOM_NOT_FOUND error is sent to the client.
     * Additional errors are reported if the game has not started, it is not the
     * player's turn, or the move is invalid.
     *
     * @param roomId Unique identifier of the target room.
     * @param move The move to be executed.
     * @param headerAccessor Provides access to the STOMP session information.
     * @return Updated GameState after a successful move; null otherwise.
     */
    @MessageMapping("/rooms/{roomId}/move")
    fun moveRoom(
        @DestinationVariable roomId: String,
        move: Move,
        headerAccessor: SimpMessageHeaderAccessor
    ): GameState? {

        val room = roomRegistry.findById(roomId)

        if (room == null) {
            sendError(
                headerAccessor.sessionId ?: "",
                ErrorCode.ROOM_NOT_FOUND,
                ROOM_NOT_FOUND_MESSAGE
            )
            return null
        }

        val sessionId = headerAccessor.sessionId ?: ""

        val stateBefore = gameService.getCurrentState(room.gameState)

        if (stateBefore.status != GameStatus.IN_PROGRESS) {
            sendError(
                sessionId,
                ErrorCode.GAME_NOT_STARTED,
                "Zug abgelehnt: Spiel läuft nicht."
            )
            return null
        }

        if (move.player != stateBefore.currentTurn) {
            sendError(
                sessionId,
                ErrorCode.NOT_YOUR_TURN,
                "Es ist nicht dein Zug!"
            )
            return null
        }

        val turnBefore = stateBefore.currentTurn

        val stateAfter = gameService.handleMove(
            room.gameState,
            move
        )

        if (stateAfter.currentTurn == turnBefore) {
            sendError(
                sessionId,
                ErrorCode.INVALID_MOVE,
                "Dieser Zug ist laut Regeln ungültig."
            )
            return null
        }

        sendRoomState(
            roomId,
            stateAfter
        )

        return stateAfter
    }


    fun handleJoin(name: String, sessionId: String) = gameService.handleJoin(name, sessionId)
    fun handleMove(move: Move) = gameService.handleMove(move)

    private fun sendError(user: String, code: ErrorCode, msg: String) {
        val errorResponse = ErrorMessage(code, msg)
        messagingTemplate.convertAndSendToUser(user, "/queue/errors", errorResponse)
    }
}