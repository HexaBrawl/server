package at.aau.hexabrawl.websocketserver.controller

import at.aau.hexabrawl.websocketserver.model.BuyUnitRequest
import at.aau.hexabrawl.websocketserver.model.ErrorCode
import at.aau.hexabrawl.websocketserver.model.GameService
import at.aau.hexabrawl.websocketserver.model.GameState
import at.aau.hexabrawl.websocketserver.model.UnitType
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
    private val gameService: GameService,
    private val contextResolver: GameContextResolver,
    private val messagingTemplate: SimpMessagingTemplate
) {

    private fun sendRoomState(roomId: String, state: GameState) {
        gameService.recomputePlayerStats(state)
        messagingTemplate.convertAndSend("/topic/rooms/${roomId}/state", state)
    }

    @MessageMapping("/rooms/{roomId}/buy-farm")
    fun buyFarmRoom(
        @DestinationVariable roomId: String,
        headerAccessor: SimpMessageHeaderAccessor
    ): GameState? {
        val sessionId = headerAccessor.sessionId ?: ""

        val ctx = contextResolver.resolveActiveGame(
            sessionId, roomId,
            requireNoPendingGift = true
        ) ?: return null
        val state = ctx.state

        val player = state.players.find { it.sessionId == sessionId } ?: return null

        if (state.currentTurn != player.name) {
            contextResolver.sendError(sessionId, ErrorCode.NOT_YOUR_TURN, "Du bist nicht am Zug.")
            return null
        }

        val cost = GameService.FARM_BASE_COST + (player.farms * GameService.FARM_COST_INCREMENT)

        if (player.gold < cost) {
            contextResolver.sendError(sessionId, ErrorCode.INSUFFICIENT_GOLD, "Nicht genug Gold.")
            return null
        }

        // Werte direkt hier updaten
        synchronized(state.lock) {
            player.gold -= cost
            player.farms += 1
            player.income = player.farms * GameService.FARM_INCOME_PER_ROUND
        }

        sendRoomState(roomId, state)
        return state
    }

    /**
     * Kauft eine Einheit fuer den Spieler im angegebenen Room und
     * platziert sie an (x, y) (#132).
     *
     * Validierungs-Reihenfolge (billig vor teuer):
     *  1. Room existiert? sonst ROOM_NOT_FOUND
     *  2. Status IN_PROGRESS? sonst GAME_NOT_STARTED
     *  3. Spieler am Zug? sonst NOT_YOUR_TURN
     *  4. Type INFANTRY/ARCHER/CAVALRY? sonst INVALID_PLACEMENT
     *  5. Zielfeld gehoert dem Spieler? sonst INVALID_PLACEMENT
     *  6. Zielfeld nicht von eigener Einheit/Basis besetzt? sonst
     *     INVALID_PLACEMENT
     *  7. Genug Gold? sonst INSUFFICIENT_GOLD
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
        val state = ctx.state

        if (request.type == UnitType.BASE || request.type == UnitType.SKELETON) {
            contextResolver.sendError(
                sessionId,
                ErrorCode.INVALID_PLACEMENT,
                "Dieser Einheitstyp kann nicht gekauft werden."
            )
            return null
        }

        // Zielfeld muss dem Spieler gehoeren (#104 Field-Ownership)
        val field = state.fields.firstOrNull { it.x == request.x && it.y == request.y }
        if (field?.owner != request.playerName) {
            contextResolver.sendError(
                sessionId,
                ErrorCode.INVALID_PLACEMENT,
                "Einheit kann nur auf eigenen Feldern platziert werden."
            )
            return null
        }

        // Zielfeld darf kein Skelett-Feld (abgeschnitten) sein
        if (field.isSkeleton) {
            contextResolver.sendError(
                sessionId,
                ErrorCode.INVALID_PLACEMENT,
                "Einheit kann nicht auf abgeschnittenen Feldern platziert werden."
            )
            return null
        }

        // Zielfeld darf nicht von eigener Einheit (inkl. BASE) besetzt sein.
        // Skelette zaehlen nicht als "besetzt" — werden vom Service entfernt.
        val occupiedByOwn = state.units.any {
            it.x == request.x && it.y == request.y &&
                    it.player == request.playerName &&
                    it.type != UnitType.SKELETON
        }
        if (occupiedByOwn) {
            contextResolver.sendError(
                sessionId,
                ErrorCode.INVALID_PLACEMENT,
                "Feld ist bereits besetzt."
            )
            return null
        }

        val player = state.players.find { it.name == request.playerName }
        if (player == null) {
            contextResolver.sendError(
                sessionId,
                ErrorCode.INVALID_PLACEMENT,
                "Spieler nicht gefunden."
            )
            return null
        }

        if (player.gold < GameService.UNIT_PRICE) {
            contextResolver.sendError(sessionId, ErrorCode.INSUFFICIENT_GOLD, "Nicht genug Gold.")
            return null
        }

        val updated = gameService.buyUnit(
            state,
            request.playerName,
            request.type,
            request.x,
            request.y
        )
        sendRoomState(roomId, updated)
        return updated
    }
}
