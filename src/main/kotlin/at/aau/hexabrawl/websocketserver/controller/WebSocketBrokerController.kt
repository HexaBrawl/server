package at.aau.hexabrawl.websocketserver.controller

import at.aau.hexabrawl.websocketserver.model.EndTurnRequest
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

    private fun sendRoomState(roomId: String, state: GameState) {
        messagingTemplate.convertAndSend(
            "/topic/rooms/$roomId/state",
            state
        )
    }

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

        if (currentState.players.size >= room.mode.maxPlayers &&
            !currentState.players.any { it.name == request.name }
        ) {
            sendError(sessionId, ErrorCode.GAME_FULL, "Beitritt verweigert: Spiel ist voll.")
            return null
        }

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

        // Snapshot vor dem Move - wenn sich nach handleMove nichts geaendert hat,
        // wurde der Move abgelehnt. Currentturn-Vergleich reicht hier nicht mehr,
        // weil der Turn mit dem Rundensystem nicht nach jedem Move switcht.
        val unitsBefore = stateBefore.units.map {
            "${it.player}-${it.type}-${it.x},${it.y}"
        }.toSet()

        val stateAfter = gameService.handleMove(room.gameState, move)

        val unitsAfter = stateAfter.units.map {
            "${it.player}-${it.type}-${it.x},${it.y}"
        }.toSet()

        if (unitsBefore == unitsAfter) {
            sendError(sessionId, ErrorCode.INVALID_MOVE, "Dieser Zug ist laut Regeln ungültig.")
            return null
        }

        sendRoomState(roomId, stateAfter)
        return stateAfter
    }

    @MessageMapping("/rooms/{roomId}/end-turn")
    fun endTurnRoom(
        @DestinationVariable roomId: String,
        request: EndTurnRequest,
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
            sendError(sessionId, ErrorCode.GAME_NOT_STARTED, "Runde beenden abgelehnt: Spiel laeuft nicht.")
            return null
        }

        if (stateBefore.currentTurn != request.playerName) {
            sendError(sessionId, ErrorCode.NOT_YOUR_TURN, "Du kannst nicht die Runde eines anderen Spielers beenden!")
            return null
        }

        val state = gameService.endTurn(room.gameState, request.playerName)
        sendRoomState(roomId, state)
        return state
    }

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
    fun endTurn(playerName: String, headerAccessor: SimpMessageHeaderAccessor): GameState? {
        val sessionId = headerAccessor.sessionId ?: ""
        val stateBefore = gameService.getCurrentState()
        if (stateBefore.status != GameStatus.IN_PROGRESS) {
            sendError(sessionId, ErrorCode.GAME_NOT_STARTED, "Runde beenden abgelehnt: Spiel laeuft nicht.")
            return null
        }
        if (stateBefore.currentTurn != playerName) {
            sendError(sessionId, ErrorCode.NOT_YOUR_TURN, "Du kannst nicht die Runde eines anderen Spielers beenden!")
            return null
        }
        return gameService.endTurn(playerName)
    }

    private fun sendError(user: String, code: ErrorCode, msg: String) {
        val errorResponse = ErrorMessage(code, msg)
        messagingTemplate.convertAndSendToUser(user, "/queue/errors", errorResponse)
    }
}
