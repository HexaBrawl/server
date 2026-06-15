package at.aau.hexabrawl.websocketserver.controller

import at.aau.hexabrawl.websocketserver.model.EndTurnRequest
import at.aau.hexabrawl.websocketserver.model.ErrorCode
import at.aau.hexabrawl.websocketserver.service.GameService
import at.aau.hexabrawl.websocketserver.model.GameState
import at.aau.hexabrawl.websocketserver.model.Move
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Controller

/**
 * Endpoints fuer Zuege und Rundenwechsel:
 *  - /rooms/{roomId}/move      Einheit ziehen / angreifen
 *  - /rooms/{roomId}/end-turn  Spieler beendet seinen Zug freiwillig
 */
@Controller
class GameTurnController(
    private val gameService: GameService,
    private val contextResolver: GameContextResolver,
    private val messagingTemplate: SimpMessagingTemplate
) {

    private fun sendRoomState(roomId: String, state: GameState) {
        gameService.recomputePlayerStats(state)
        messagingTemplate.convertAndSend("/topic/rooms/${roomId}/state", state)
    }

    @MessageMapping("/rooms/{roomId}/move")
    fun moveRoom(
        @DestinationVariable roomId: String,
        move: Move,
        headerAccessor: SimpMessageHeaderAccessor
    ): GameState? {
        val sessionId = headerAccessor.sessionId ?: ""

        val ctx = contextResolver.resolveActiveGame(
            sessionId, roomId,
            gameNotStartedMessage = "Zug abgelehnt: Spiel läuft nicht.",
            notYourTurnMessage = "Es ist nicht dein Zug!",
            expectedCurrentTurn = move.player,
            requireNoPendingGift = true
        ) ?: return null

        val stateBefore = gameService.getCurrentState(ctx.room.gameState)

        // Snapshot vor dem Move - wenn sich nach handleMove nichts geaendert hat,
        // wurde der Move abgelehnt. Currentturn-Vergleich reicht hier nicht mehr,
        // weil der Turn mit dem Rundensystem nicht nach jedem Move switcht.
        val unitsBefore = stateBefore.units.map {
            "${it.player}-${it.type}-${it.x},${it.y}"
        }.toSet()

        val stateAfter = gameService.handleMove(ctx.room.gameState, move)

        val unitsAfter = stateAfter.units.map {
            "${it.player}-${it.type}-${it.x},${it.y}"
        }.toSet()

        if (unitsBefore == unitsAfter) {
            contextResolver.sendError(sessionId, ErrorCode.INVALID_MOVE, "Dieser Zug ist laut Regeln ungültig.")
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

        val ctx = contextResolver.resolveActiveGame(
            sessionId, roomId,
            gameNotStartedMessage = "Runde beenden abgelehnt: Spiel laeuft nicht.",
            notYourTurnMessage = "Du kannst nicht die Runde eines anderen Spielers beenden!",
            expectedCurrentTurn = request.playerName,
            requireNoPendingGift = true
        ) ?: return null

        val state = gameService.endTurn(ctx.room.gameState, request.playerName)
        sendRoomState(roomId, state)
        return state
    }
}
