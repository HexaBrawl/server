package at.aau.hexabrawl.websocketserver.service

import at.aau.hexabrawl.websocketserver.model.CombatResult
import at.aau.hexabrawl.websocketserver.model.GameUnit
import at.aau.hexabrawl.websocketserver.model.UnitType
import org.springframework.stereotype.Service

@Service
class CombatService {

    /**
     * Pure combat resolution – kein State-Mutation.
     * Aufrufen wenn attacker auf ein feindliches Feld zieht.
     * Voraussetzung: Adjacency-Check und Eigentumsprüfung bereits durch Move-Logik validiert.
     */
    fun resolveCombat(attacker: GameUnit, defender: GameUnit): CombatResult {
        require(attacker.type != UnitType.SKELETON) { "SKELETON cannot attack" }
        require(defender.type != UnitType.SKELETON) { "SKELETON cannot be attacked" }

        return when {
            attacker.type.beats(defender.type) -> CombatResult(
                attackerSurvived = true,
                defenderSurvived = false,
                winnerId = attacker.player,
                tileX = defender.x,
                tileY = defender.y
            )
            defender.type.beats(attacker.type) -> CombatResult(
                attackerSurvived = false,
                defenderSurvived = true,
                winnerId = defender.player,
                tileX = defender.x,
                tileY = defender.y
            )
            else -> CombatResult(
                attackerSurvived = false,
                defenderSurvived = false,
                winnerId = null,
                tileX = defender.x,
                tileY = defender.y
            )
        }
    }

    fun applyCombatResult(result: CombatResult, attacker: GameUnit, defender: GameUnit) {
        if (result.attackerSurvived) {
            attacker.x = result.tileX
            attacker.y = result.tileY
        }
    }
}