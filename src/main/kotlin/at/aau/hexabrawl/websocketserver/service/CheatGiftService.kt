package at.aau.hexabrawl.websocketserver.service

import at.aau.hexabrawl.websocketserver.model.GameState
import at.aau.hexabrawl.websocketserver.model.PendingGift
import org.springframework.stereotype.Service

/**
 * Schummel-Geschenk-Logik (Cheat-Gift-Feature).
 *
 * Spielmechanik:
 *  - Ein Spieler oeffnet ein Geschenk und bekommt sofort delta Gold gebucht
 *    (kann auch negativ sein, mit Cap bei 0).
 *  - Alle anderen Spieler bekommen die Chance zu stehlen — Owner-Gold zurueck,
 *    Stealer-Gold +delta. Erster Stealer gewinnt.
 *  - hasUsedGift verhindert mehrfaches Oeffnen pro Spieler.
 *
 * Validierungen liegen im Controller — diese Service-Methoden mutieren nur.
 */
@Service
class CheatGiftService {

    fun claimCheatGift(
        state: GameState,
        playerName: String,
        delta: Int
    ): GameState = synchronized(state.lock) {
        val player = state.players.find { it.name == playerName } ?: return state

        player.gold += delta
        if (player.gold < 0) player.gold = 0
        player.hasUsedGift = true

        state.pendingGift = PendingGift(
            ownerName = player.name,
            delta = delta,
            pendingDecisions = state.players.size - 1
        )
        return state
    }

    fun respondCheatSteal(
        state: GameState,
        playerName: String,
        accept: Boolean
    ): GameState = synchronized(state.lock) {
        val gift = state.pendingGift ?: return state
        val player = state.players.find { it.name == playerName } ?: return state

        if (accept) {
            val owner = state.players.first { it.name == gift.ownerName }
            owner.gold -= gift.delta
            if (owner.gold < 0) owner.gold = 0
            player.gold += gift.delta
            if (player.gold < 0) player.gold = 0
            state.pendingGift = null
        } else {
            val remaining = gift.pendingDecisions - 1
            state.pendingGift = if (remaining > 0) {
                gift.copy(pendingDecisions = remaining)
            } else {
                null
            }
        }
        return state
    }
}