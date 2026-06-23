package at.aau.hexabrawl.websocketserver.service

import at.aau.hexabrawl.websocketserver.model.GameState
import at.aau.hexabrawl.websocketserver.model.GameStatus
import at.aau.hexabrawl.websocketserver.model.GameUnit
import at.aau.hexabrawl.websocketserver.model.HexDistance
import at.aau.hexabrawl.websocketserver.model.Move
import at.aau.hexabrawl.websocketserver.model.UnitType
import org.springframework.stereotype.Service

/** Ergebnis eines [TurnService.handleMove]-Aufrufs. */
sealed class MoveResult(open val state: GameState) {
    /** Zug wurde regelkonform ausgeführt; [state] enthält den aktualisierten Spielzustand. */
    data class Applied(override val state: GameState) : MoveResult(state)
    /** Zug wurde abgelehnt, weil er gegen Spielregeln verstößt; [state] bleibt unverändert. */
    data class Rejected(override val state: GameState) : MoveResult(state)
}

/**
 * Move-Pipeline und Turn-Management.
 *
 * Verarbeitet einzelne Moves (handleMove), führt Combat über den CombatService aus,
 * markiert Einheiten als bewegt, ruft die Connectivity-Neuberechnung und
 * Win-Condition-Check an, schaltet Turns weiter und buucht beim Turn-Wechsel
 * die Wirtschaft des scheidenden Spielers.
 */
@Service
class TurnService(
    private val combatService: CombatService,
    private val connectivityService: ConnectivityService,
    private val economyService: EconomyService,
    private val playerService: PlayerService
) {

    companion object {
        const val MAX_MOVE_DISTANCE = 2
    }

    /**
     * Verarbeitet einen Spielzug: validiert Entfernung, Eigentum, Bewegungs-Status
     * und führt ggf. Combat über den [CombatService] aus.
     *
     * @param state Aktueller Spielzustand (wird mutiert).
     * @param move  Der auszuführende Zug mit Quell- und Zielkoordinaten.
     * @return [MoveResult.Applied] bei Erfolg, [MoveResult.Rejected] bei Regelverstoß.
     */
    fun handleMove(state: GameState, move: Move): MoveResult = synchronized(state.lock) {
        if (state.status != GameStatus.IN_PROGRESS) return MoveResult.Rejected(state)
        if (move.player != state.currentTurn) return MoveResult.Rejected(state)

        if (move.type == UnitType.BASE) return MoveResult.Rejected(state)

        val unit = state.units.firstOrNull {
            it.player == move.player &&
                    it.type == move.type &&
                    it.type != UnitType.SKELETON &&
                    it.x == move.fromX &&
                    it.y == move.fromY
        } ?: return MoveResult.Rejected(state)

        val distance = HexDistance.between(move.fromX, move.fromY, move.toX, move.toY)
        if (distance == 0 || distance > MAX_MOVE_DISTANCE) return MoveResult.Rejected(state)

        if (unit.hasMovedThisTurn) return MoveResult.Rejected(state)

        val friendlyOnTarget = state.units.any {
            it.x == move.toX && it.y == move.toY &&
                    it.player == move.player &&
                    it.type != UnitType.SKELETON
        }
        if (friendlyOnTarget) return MoveResult.Rejected(state)

        val targetField = state.fields.firstOrNull { it.x == move.toX && it.y == move.toY }
        val isOwnField = targetField?.owner == move.player
        val isBorderField = isAdjacentToOwnTerritory(state, move.toX, move.toY, move.player)
        if (!isOwnField && !isBorderField) return MoveResult.Rejected(state)

        state.units.removeIf {
            it.x == move.toX && it.y == move.toY && it.type == UnitType.SKELETON
        }

        val enemyOnTarget = state.units.firstOrNull {
            it.x == move.toX && it.y == move.toY &&
                    it.player != move.player &&
                    it.type != UnitType.SKELETON
        }

        if (enemyOnTarget != null) {
            if (enemyOnTarget.type == UnitType.BASE) {
                state.units.remove(enemyOnTarget)
                unit.x = move.toX
                unit.y = move.toY
                finishMove(state, unit, move.player)
                playerService.checkWinCondition(state)
                return MoveResult.Applied(state)
            }

            val result = combatService.resolveCombat(unit, enemyOnTarget)
            combatService.applyCombatResult(result, unit, enemyOnTarget)
            if (!result.defenderSurvived) state.units.remove(enemyOnTarget)
            if (!result.attackerSurvived) state.units.remove(unit)
            finishMove(state, unit, move.player)
            playerService.checkWinCondition(state)
            return MoveResult.Applied(state)
        }

        unit.x = move.toX
        unit.y = move.toY

        finishMove(state, unit, move.player)

        playerService.checkWinCondition(state)
        return MoveResult.Applied(state)
    }

    /**
     * Beendet den Zug von [playerName] und schaltet auf den nächsten Spieler weiter.
     * Bucht dabei die Wirtschaft (Income/Upkeep) des scheidenden Spielers.
     *
     * @param state      Aktueller Spielzustand.
     * @param playerName Name des Spielers, der seinen Zug beendet.
     * @return Den aktualisierten [GameState].
     */
    fun endTurn(state: GameState, playerName: String): GameState = synchronized(state.lock) {
        if (state.status != GameStatus.IN_PROGRESS) return state
        if (state.currentTurn != playerName) return state
        switchTurn(state)
        return state
    }

    /**
     * Schließt einen Zug ab: setzt hasMovedThisTurn, übernimmt das Zielfeld,
     * und ruft [ConnectivityService.recomputeConnectivity] auf.
     */
    private fun finishMove(state: GameState, unit: GameUnit, playerName: String) {
        if (unit in state.units) {
            unit.hasMovedThisTurn = true
            state.fields.firstOrNull { it.x == unit.x && it.y == unit.y }
                ?.let {
                    it.owner = playerName
                    it.isSkeleton = false
                }
        }
        connectivityService.recomputeConnectivity(state)
    }

    /**
     * Wechselt den aktiven Spieler zum nächsten in der Runde,
     * bucht die Wirtschaft des scheidenden Spielers und setzt hasMovedThisTurn zurück.
     */
    private fun switchTurn(state: GameState) {
        if (state.players.isEmpty()) return

        // Wirtschaft des Spielers, dessen Zug gerade endet
        state.players.firstOrNull { it.name == state.currentTurn }?.let {
            economyService.applyEconomy(state, it)
        }

        if (state.players.size == 2) {
            val p1 = state.players[0]
            val p2 = state.players[1]
            state.currentTurn = if (state.currentTurn == p1.name) p2.name else p1.name
        } else {
            val currentIndex = state.players.indexOfFirst { it.name == state.currentTurn }
            state.currentTurn = if (currentIndex != -1) {
                state.players[(currentIndex + 1) % state.players.size].name
            } else {
                state.players.firstOrNull()?.name
            }
        }

        state.units.forEach { it.hasMovedThisTurn = false }
    }

    /**
     * Prüft, ob das Feld ([x], [y]) an ein Feld angrenzt, das [playerName] gehört.
     * Wird verwendet, um Angriffe auf Randfelder des gegnerischen Gebiets zu erlauben.
     */
    private fun isAdjacentToOwnTerritory(state: GameState, x: Int, y: Int, playerName: String): Boolean {
        return state.fields.any { field ->
            field.owner == playerName &&
                    HexDistance.between(field.x, field.y, x, y) == 1
        }
    }
}
