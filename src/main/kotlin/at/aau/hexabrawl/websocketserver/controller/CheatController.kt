package at.aau.hexabrawl.websocketserver.controller

import at.aau.hexabrawl.websocketserver.model.ClaimGiftRequest
import at.aau.hexabrawl.websocketserver.model.ErrorCode
import at.aau.hexabrawl.websocketserver.model.GameService
import at.aau.hexabrawl.websocketserver.model.GameState
import at.aau.hexabrawl.websocketserver.model.StealResponseRequest
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Controller

/**
 * Endpoints fuer das Schummel-Geschenk-Feature:
 *  - /rooms/{roomId}/cheat/claim-gift     Spieler oeffnet ein Geschenk
 *  - /rooms/{roomId}/cheat/respond-steal  Antwort auf das Geschenk
 *                                         (Stehlen oder Ablehnen)
 */
@Controller
class CheatController(
    private val gameService: GameService,
    private val contextResolver: GameContextResolver,
    private val messagingTemplate: SimpMessagingTemplate
) {

    private fun sendRoomState(roomId: String, state: GameState) {
        gameService.recomputePlayerStats(state)
        messagingTemplate.convertAndSend("/topic/rooms/${roomId}/state", state)
    }

    /**
     * Oeffnet ein Schummel-Geschenk fuer den Spieler.
     *
     * Validierungs-Reihenfolge (billig vor teuer):
     *  1. Room existiert? sonst ROOM_NOT_FOUND
     *  2. Status IN_PROGRESS? sonst GAME_NOT_STARTED
     *  3. delta in -10..10? sonst INVALID_CHEAT_DELTA
     *  4. Kein pendingGift aktiv? sonst CHEAT_ALREADY_PENDING
     *  5. Spieler existiert? sonst no-op
     *  6. hasUsedGift false? sonst CHEAT_ALREADY_USED
     */
    @MessageMapping("/rooms/{roomId}/cheat/claim-gift")
    fun claimCheatGiftRoom(
        @DestinationVariable roomId: String,
        request: ClaimGiftRequest,
        headerAccessor: SimpMessageHeaderAccessor
    ): GameState? {
        val sessionId = headerAccessor.sessionId ?: ""

        val ctx = contextResolver.resolveActiveGame(sessionId, roomId) ?: return null
        val state = ctx.state

        if (request.delta !in -10..10) {
            contextResolver.sendError(sessionId, ErrorCode.INVALID_CHEAT_DELTA, "Delta muss zwischen -10 und +10 liegen.")
            return null
        }

        if (state.pendingGift != null) {
            contextResolver.sendError(sessionId, ErrorCode.CHEAT_ALREADY_PENDING, "Es laeuft bereits ein Geschenk.")
            return null
        }

        val player = state.players.find { it.name == request.playerName }
        if (player == null) return null

        if (player.hasUsedGift) {
            contextResolver.sendError(sessionId, ErrorCode.CHEAT_ALREADY_USED, "Du hast dein Geschenk schon benutzt.")
            return null
        }

        val updated = gameService.claimCheatGift(state, request.playerName, request.delta)
        sendRoomState(roomId, updated)
        return updated
    }

    /**
     * Antwort auf das Schummel-Geschenk: "Ja klauen" oder "Nein".
     *
     * Validierungs-Reihenfolge (billig vor teuer):
     *  1. Room existiert? sonst ROOM_NOT_FOUND
     *  2. pendingGift aktiv? sonst NO_PENDING_GIFT
     *  3. playerName != owner? sonst OWNER_CANNOT_STEAL
     *  4. Spieler existiert? sonst no-op
     */
    @MessageMapping("/rooms/{roomId}/cheat/respond-steal")
    fun respondCheatStealRoom(
        @DestinationVariable roomId: String,
        request: StealResponseRequest,
        headerAccessor: SimpMessageHeaderAccessor
    ): GameState? {
        val sessionId = headerAccessor.sessionId ?: ""

        val ctx = contextResolver.resolveRoom(sessionId, roomId) ?: return null
        val state = ctx.state

        val gift = state.pendingGift
        if (gift == null) {
            contextResolver.sendError(sessionId, ErrorCode.NO_PENDING_GIFT, "Es laeuft kein Geschenk.")
            return null
        }

        if (request.playerName == gift.ownerName) {
            contextResolver.sendError(sessionId, ErrorCode.OWNER_CANNOT_STEAL, "Du kannst dein eigenes Geschenk nicht stehlen.")
            return null
        }

        val player = state.players.find { it.name == request.playerName }
        if (player == null) return null

        val updated = gameService.respondCheatSteal(state, request.playerName, request.accept)
        sendRoomState(roomId, updated)
        return updated
    }
}
