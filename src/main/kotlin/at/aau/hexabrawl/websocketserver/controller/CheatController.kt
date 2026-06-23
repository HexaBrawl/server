package at.aau.hexabrawl.websocketserver.controller

import at.aau.hexabrawl.websocketserver.model.ClaimGiftRequest
import at.aau.hexabrawl.websocketserver.model.GameState
import at.aau.hexabrawl.websocketserver.model.StealResponseRequest
import at.aau.hexabrawl.websocketserver.service.CheatGiftService
import at.aau.hexabrawl.websocketserver.service.ClaimGiftResult
import at.aau.hexabrawl.websocketserver.service.EconomyService
import at.aau.hexabrawl.websocketserver.service.StealResult
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
    private val cheatGiftService: CheatGiftService,
    private val economyService: EconomyService,
    private val contextResolver: GameContextResolver,
    private val messagingTemplate: SimpMessagingTemplate
) {

    private fun sendRoomState(roomId: String, state: GameState) {
        economyService.recomputePlayerStats(state)
        messagingTemplate.convertAndSend("/topic/rooms/${roomId}/state", state)
    }

    @MessageMapping("/rooms/{roomId}/cheat/claim-gift")
    fun claimCheatGiftRoom(
        @DestinationVariable roomId: String,
        request: ClaimGiftRequest,
        headerAccessor: SimpMessageHeaderAccessor
    ): GameState? {
        val sessionId = headerAccessor.sessionId ?: ""

        val ctx = contextResolver.resolveActiveGame(
            sessionId, roomId,
            notYourTurnMessage = "Du kannst das Geschenk nur waehrend deines Zuges oeffnen.",
            expectedCurrentTurn = request.playerName
        ) ?: return null

        val result = cheatGiftService.claimCheatGift(ctx.state, request.playerName, request.delta)

        if (result is ClaimGiftResult.Rejected) {
            contextResolver.sendError(sessionId, result.errorCode, result.message)
            return null
        }

        val claimed = (result as ClaimGiftResult.Claimed).state
        sendRoomState(roomId, claimed)
        return claimed
    }

    @MessageMapping("/rooms/{roomId}/cheat/respond-steal")
    fun respondCheatStealRoom(
        @DestinationVariable roomId: String,
        request: StealResponseRequest,
        headerAccessor: SimpMessageHeaderAccessor
    ): GameState? {
        val sessionId = headerAccessor.sessionId ?: ""

        val ctx = contextResolver.resolveRoom(sessionId, roomId) ?: return null

        val result = cheatGiftService.respondCheatSteal(ctx.state, request.playerName, request.accept)

        if (result is StealResult.Rejected) {
            contextResolver.sendError(sessionId, result.errorCode, result.message)
            return null
        }

        val resolved = (result as StealResult.Resolved).state
        sendRoomState(roomId, resolved)
        return resolved
    }
}
