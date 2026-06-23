package at.aau.hexabrawl.websocketserver.service

import at.aau.hexabrawl.websocketserver.model.ErrorCode
import at.aau.hexabrawl.websocketserver.model.GameState
import at.aau.hexabrawl.websocketserver.model.GameUnit
import at.aau.hexabrawl.websocketserver.model.Player
import at.aau.hexabrawl.websocketserver.model.UnitType
import org.springframework.stereotype.Service

/**
 * Wirtschafts-Logik des Spiels: Income- und Upkeep-Berechnung, Buy-Unit,
 * Per-Player-Economy-Tick beim Turn-Ende.
 *
 * Income setzt sich zusammen aus Feld-Anteil (1 Gold pro eigenem,
 * nicht-skeletonisiertem Feld) + Farm-Anteil (3 Gold pro Farm).
 *
 * Upkeep ist progressiv: 1. Einheit = 3, 2. = 4, 3. = 5, usw.
 */
@Service
class EconomyService {

    companion object {
        const val STARTING_GOLD = 6
        const val FARM_INCOME_PER_ROUND = 3
        const val FIELD_INCOME_PER_ROUND = 1
        const val FARM_BASE_COST = 10
        const val FARM_COST_INCREMENT = 1
        const val UNIT_PRICE = 5
    }

    fun computeIncome(player: Player, state: GameState): Int {
        val ownedFields = state.fields.count { it.owner == player.name && !it.isSkeleton }
        return ownedFields * FIELD_INCOME_PER_ROUND + player.farms * FARM_INCOME_PER_ROUND
    }

    fun computeUpkeep(player: Player, state: GameState): Int {
        val unitCount = state.units.count {
            it.player == player.name && it.type != UnitType.SKELETON && it.type != UnitType.BASE
        }
        return (0 until unitCount).sumOf { 3 + it }
    }

    fun recomputePlayerStats(state: GameState) {
        state.players.forEach { player ->
            player.income = computeIncome(player, state)
            player.upkeep = computeUpkeep(player, state)
        }
    }

    /**
     * Wirtschafts-Rundenabschluss fuer einen Spieler.
     * Farm- + Feld-Einkommen gutschreiben, Unterhalt abziehen.
     * Bei Insolvenz: Gold auf 0, alle lebenden Truppen → SKELETON.
     */
    fun applyEconomy(state: GameState, player: Player) {
        player.gold += computeIncome(player, state)
        val upkeep = computeUpkeep(player, state)
        val playerUnits = state.units.filter {
            it.player == player.name && it.type != UnitType.SKELETON && it.type != UnitType.BASE
        }

        if (player.gold >= upkeep) {
            player.gold -= upkeep
        } else {
            player.gold = 0
            playerUnits.forEach { it.type = UnitType.SKELETON }
        }
    }

    /**
     * Kauft eine Farm fuer den Spieler.
     * Gibt true zurueck wenn der Kauf erfolgreich war, false bei zu wenig Gold.
     */
    fun buyFarm(state: GameState, player: Player): Boolean = synchronized(state.lock) {
        val cost = FARM_BASE_COST + (player.farms * FARM_COST_INCREMENT)
        if (player.gold < cost) return false
        player.gold -= cost
        player.farms += 1
        return true
    }

    fun buyUnit(
        state: GameState,
        playerName: String,
        type: UnitType,
        x: Int,
        y: Int
    ): BuyUnitResult = synchronized(state.lock) {
        if (type == UnitType.BASE || type == UnitType.SKELETON) {
            return BuyUnitResult.Rejected(ErrorCode.INVALID_PLACEMENT, "Dieser Einheitstyp kann nicht gekauft werden.")
        }

        val field = state.fields.firstOrNull { it.x == x && it.y == y }
        if (field?.owner != playerName) {
            return BuyUnitResult.Rejected(ErrorCode.INVALID_PLACEMENT, "Einheit kann nur auf eigenen Feldern platziert werden.")
        }

        if (field.isSkeleton) {
            return BuyUnitResult.Rejected(ErrorCode.INVALID_PLACEMENT, "Einheit kann nicht auf abgeschnittenen Feldern platziert werden.")
        }

        val occupiedByOwn = state.units.any {
            it.x == x && it.y == y && it.player == playerName && it.type != UnitType.SKELETON
        }
        if (occupiedByOwn) {
            return BuyUnitResult.Rejected(ErrorCode.INVALID_PLACEMENT, "Feld ist bereits besetzt.")
        }

        val player = state.players.find { it.name == playerName }
            ?: return BuyUnitResult.Rejected(ErrorCode.INVALID_PLACEMENT, "Spieler nicht gefunden.")

        if (player.gold < UNIT_PRICE) {
            return BuyUnitResult.Rejected(ErrorCode.INSUFFICIENT_GOLD, "Nicht genug Gold.")
        }

        state.units.removeIf { it.x == x && it.y == y && it.type == UnitType.SKELETON }
        player.gold -= UNIT_PRICE
        state.units.add(GameUnit(player = playerName, x = x, y = y, type = type, hasMovedThisTurn = true))

        return BuyUnitResult.Placed(state)
    }
}
