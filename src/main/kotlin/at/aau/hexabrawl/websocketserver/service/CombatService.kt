package at.aau.hexabrawl.websocketserver.service

import at.aau.hexabrawl.websocketserver.model.CombatResult
import at.aau.hexabrawl.websocketserver.model.GameUnit
import at.aau.hexabrawl.websocketserver.model.UnitType
import org.springframework.stereotype.Service

/**
 * Stein-Schere-Papier-Combat zwischen zwei Einheiten.
 *
 * Loest den Kampf rein rechnerisch ueber [UnitType.beats] auf und liefert
 * ein [CombatResult]; State-Mutation auf den GameUnit-Objekten passiert
 * separat in [applyCombatResult]. Skelette koennen weder angreifen noch
 * angegriffen werden.
 */
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

    /**
     * Wendet das [CombatResult] auf die Einheiten an: bewegt den Angreifer auf das
     * Zielfeld, wenn er überlebt hat. Entfernen toter Einheiten übernimmt der Aufrufer.
     *
     * @param result   Ergebnis aus [resolveCombat].
     * @param attacker Angreifende Einheit.
     * @param defender Verteidigende Einheit.
     */
    fun applyCombatResult(result: CombatResult, attacker: GameUnit, defender: GameUnit) {
        if (result.attackerSurvived) {
            attacker.x = result.tileX
            attacker.y = result.tileY
        }
    }
}