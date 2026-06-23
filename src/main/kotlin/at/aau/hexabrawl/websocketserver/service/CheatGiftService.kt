package at.aau.hexabrawl.websocketserver.service

import at.aau.hexabrawl.websocketserver.model.ErrorCode
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
 */
@Service
class CheatGiftService {

    fun claimCheatGift(
        state: GameState,
        playerName: String,
        delta: Int
    ): ClaimGiftResult = synchronized(state.lock) {
        if (delta !in -10..10) {
            return ClaimGiftResult.Rejected(ErrorCode.INVALID_CHEAT_DELTA, "Delta muss zwischen -10 und +10 liegen.")
        }

        if (state.pendingGift != null) {
            return ClaimGiftResult.Rejected(ErrorCode.CHEAT_ALREADY_PENDING, "Es laeuft bereits ein Geschenk.")
        }

        val player = state.players.find { it.name == playerName }
            ?: return ClaimGiftResult.Rejected(ErrorCode.INVALID_MOVE, "Spieler nicht gefunden.")

        if (player.hasUsedGift) {
            return ClaimGiftResult.Rejected(ErrorCode.CHEAT_ALREADY_USED, "Du hast dein Geschenk schon benutzt.")
        }

        player.gold += delta
        if (player.gold < 0) player.gold = 0
        player.hasUsedGift = true

        state.pendingGift = PendingGift(
            ownerName = player.name,
            delta = delta,
            pendingDecisions = state.players.size - 1
        )
        return ClaimGiftResult.Claimed(state)
    }

    fun respondCheatSteal(
        state: GameState,
        playerName: String,
        accept: Boolean
    ): StealResult = synchronized(state.lock) {
        val gift = state.pendingGift
            ?: return StealResult.Rejected(ErrorCode.NO_PENDING_GIFT, "Es laeuft kein Geschenk.")

        if (playerName == gift.ownerName) {
            return StealResult.Rejected(ErrorCode.OWNER_CANNOT_STEAL, "Du kannst dein eigenes Geschenk nicht stehlen.")
        }

        val player = state.players.find { it.name == playerName }
            ?: return StealResult.Rejected(ErrorCode.INVALID_MOVE, "Spieler nicht gefunden.")

        if (accept) {
            val owner = state.players.first { it.name == gift.ownerName }
            owner.gold -= gift.delta
            if (owner.gold < 0) owner.gold = 0
            player.gold += gift.delta
            if (player.gold < 0) player.gold = 0
            state.pendingGift = null
        } else {
            val remaining = gift.pendingDecisions - 1
            state.pendingGift = if (remaining > 0) gift.copy(pendingDecisions = remaining) else null
        }
        return StealResult.Resolved(state)
    }
}
