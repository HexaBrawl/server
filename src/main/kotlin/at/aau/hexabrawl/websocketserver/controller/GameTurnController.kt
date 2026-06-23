package at.aau.hexabrawl.websocketserver.controller

import at.aau.hexabrawl.websocketserver.model.EndTurnRequest
import at.aau.hexabrawl.websocketserver.model.ErrorCode
import at.aau.hexabrawl.websocketserver.model.GameState
import at.aau.hexabrawl.websocketserver.model.Move
import at.aau.hexabrawl.websocketserver.service.EconomyService
import at.aau.hexabrawl.websocketserver.service.MoveResult
import at.aau.hexabrawl.websocketserver.service.TurnService
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
    private val turnService: TurnService,
    private val economyService: EconomyService,
    private val contextResolver: GameContextResolver,
    private val messagingTemplate: SimpMessagingTemplate
) {

    /** Aktualisiert Income/Upkeep und broadcastet den [state] an alle Subscriber des Raums. */
    private fun sendRoomState(roomId: String, state: GameState) {
        economyService.recomputePlayerStats(state)
        messagingTemplate.convertAndSend("/topic/rooms/${roomId}/state", state)
    }

    /**
     * Verarbeitet einen Einheitenzug für den Raum [roomId].
     * Validiert Spielberechtigung und delegiert an [TurnService.handleMove].
     *
     * @param roomId         ID des Zielraums.
     * @param move           Zug-Objekt mit Spieler, Einheitstyp und Koordinaten.
     * @param headerAccessor STOMP-Header zum Auslesen der Session-ID.
     * @return Aktualisierter [GameState] oder null bei Ablehnung.
     */
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

        val result = turnService.handleMove(ctx.room.gameState, move)

        if (result is MoveResult.Rejected) {
            contextResolver.sendError(sessionId, ErrorCode.INVALID_MOVE, "Dieser Zug ist laut Regeln ungültig.")
            return null
        }

        sendRoomState(roomId, result.state)
        return result.state
    }

    /**
     * Beendet den Zug des Spielers für den Raum [roomId] und schaltet auf den nächsten Spieler weiter.
     *
     * @param roomId         ID des Zielraums.
     * @param request        Enthält den Namen des Spielers, der seinen Zug beendet.
     * @param headerAccessor STOMP-Header zum Auslesen der Session-ID.
     * @return Aktualisierter [GameState] oder null bei Ablehnung.
     */
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

        val state = turnService.endTurn(ctx.room.gameState, request.playerName)
        sendRoomState(roomId, state)
        return state
    }
}
