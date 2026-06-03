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

    @MessageMapping("/rooms/{roomId}/join")
    fun joinRoom(
        @DestinationVariable roomId: String,
        playerName: String,
        headerAccessor: SimpMessageHeaderAccessor
    ): GameState? {

        val room = roomRegistry.findById(roomId)
            ?: return null

        val sessionId = headerAccessor.sessionId ?: ""

        val currentState = gameService.getCurrentState(room.gameState)

        if (currentState.players.size >= GameService.MAX_PLAYERS &&
            !currentState.players.any { it.name == playerName }) {

            sendError(
                sessionId,
                ErrorCode.GAME_FULL,
                "Beitritt verweigert: Spiel ist voll."
            )

            return null
        }

        return gameService.handleJoin(
            room.gameState,
            playerName,
            sessionId
        )
    }

    @MessageMapping("/init")
    @SendTo("/topic/game")
    fun init(): GameState {
        return gameService.getCurrentState()
    }

    @MessageMapping("/rooms/{roomId}/init")
    fun initRoom(
        @DestinationVariable roomId: String
    ): GameState? {
        val room = roomRegistry.findById(roomId)
            ?: return null

        return gameService.getCurrentState(
            room.gameState
        )
    }

    @MessageMapping("/move")
    @SendTo("/topic/game")
    fun move(move: Move, headerAccessor: SimpMessageHeaderAccessor): GameState? {
        val sessionId = headerAccessor.sessionId ?: ""
        val stateBefore = gameService.getCurrentState()

        if (stateBefore.status != GameStatus.IN_PROGRESS) {
            sendError(sessionId, ErrorCode.GAME_NOT_STARTED, "Zug abgelehnt: Spiel läuft nicht.")
            return null
        }

        if (move.player != stateBefore.currentTurn) {
            sendError(sessionId, ErrorCode.NOT_YOUR_TURN, "Es ist nicht dein Zug!")
            return null
        }

        val turnBefore = stateBefore.currentTurn
        val stateAfter = gameService.handleMove(move)

        if (stateAfter.currentTurn == turnBefore) {
            sendError(sessionId, ErrorCode.INVALID_MOVE, "Dieser Zug ist laut Regeln ungültig.")
            return null
        }

        return stateAfter
    }

    @MessageMapping("/rooms/{roomId}/move")
    fun moveRoom(
        @DestinationVariable roomId: String,
        move: Move,
        headerAccessor: SimpMessageHeaderAccessor
    ): GameState? {

        val room = roomRegistry.findById(roomId)
            ?: return null

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

        return stateAfter
    }


    fun handleJoin(name: String, sessionId: String) = gameService.handleJoin(name, sessionId)
    fun handleMove(move: Move) = gameService.handleMove(move)

    private fun sendError(user: String, code: ErrorCode, msg: String) {
        val errorResponse = ErrorMessage(code, msg)
        messagingTemplate.convertAndSendToUser(user, "/queue/errors", errorResponse)
    }
}