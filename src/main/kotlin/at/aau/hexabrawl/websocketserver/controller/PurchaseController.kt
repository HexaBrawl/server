package at.aau.hexabrawl.websocketserver.controller

import at.aau.hexabrawl.websocketserver.model.BuyFarmRequest
import at.aau.hexabrawl.websocketserver.model.BuyUnitRequest
import at.aau.hexabrawl.websocketserver.model.ErrorCode
import at.aau.hexabrawl.websocketserver.model.GameState
import at.aau.hexabrawl.websocketserver.service.BuyUnitResult
import at.aau.hexabrawl.websocketserver.service.EconomyService
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Controller

/**
 * Endpoints fuer Spieler-Kaeufe waehrend des laufenden Spiels:
 *  - /rooms/{roomId}/buy-farm  Farm bauen (erhoeht Income)
 *  - /rooms/{roomId}/buy-unit  Einheit kaufen und platzieren (#132)
 */
@Controller
class PurchaseController(
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
     * Kauft eine Farm für den Spieler im Raum [roomId].
     *
     * @param roomId         ID des Zielraums.
     * @param request        Enthält den Spielernamen, der die Farm kauft.
     * @param headerAccessor STOMP-Header zum Auslesen der Session-ID.
     * @return Aktualisierter [GameState] oder null bei Ablehnung.
     */
    @MessageMapping("/rooms/{roomId}/buy-farm")
    fun buyFarmRoom(
        @DestinationVariable roomId: String,
        request: BuyFarmRequest,
        headerAccessor: SimpMessageHeaderAccessor
    ): GameState? {
        val sessionId = headerAccessor.sessionId ?: ""

        val ctx = contextResolver.resolveActiveGame(
            sessionId, roomId,
            gameNotStartedMessage = "Kauf abgelehnt: Spiel laeuft nicht.",
            notYourTurnMessage = "Du bist nicht am Zug.",
            expectedCurrentTurn = request.playerName,
            requireNoPendingGift = true
        ) ?: return null
        val state = ctx.state

        val player = state.players.find { it.name == request.playerName } ?: return null

        if (!economyService.buyFarm(state, player)) {
            contextResolver.sendError(sessionId, ErrorCode.INSUFFICIENT_GOLD, "Nicht genug Gold.")
            return null
        }

        sendRoomState(roomId, state)
        return state
    }

    /**
     * Kauft eine Einheit und platziert sie auf dem angegebenen Feld im Raum [roomId].
     *
     * @param roomId         ID des Zielraums.
     * @param request        Enthält Spielername, Einheitentyp und Zielkoordinaten.
     * @param headerAccessor STOMP-Header zum Auslesen der Session-ID.
     * @return Aktualisierter [GameState] oder null bei Ablehnung.
     */
    @MessageMapping("/rooms/{roomId}/buy-unit")
    fun buyUnitRoom(
        @DestinationVariable roomId: String,
        request: BuyUnitRequest,
        headerAccessor: SimpMessageHeaderAccessor
    ): GameState? {
        val sessionId = headerAccessor.sessionId ?: ""

        val ctx = contextResolver.resolveActiveGame(
            sessionId, roomId,
            gameNotStartedMessage = "Kauf abgelehnt: Spiel laeuft nicht.",
            notYourTurnMessage = "Es ist nicht dein Zug.",
            expectedCurrentTurn = request.playerName,
            requireNoPendingGift = true
        ) ?: return null

        val result = economyService.buyUnit(ctx.state, request.playerName, request.type, request.x, request.y)

        if (result is BuyUnitResult.Rejected) {
            contextResolver.sendError(sessionId, result.errorCode, result.message)
            return null
        }

        val placed = (result as BuyUnitResult.Placed).state
        sendRoomState(roomId, placed)
        return placed
    }
}
