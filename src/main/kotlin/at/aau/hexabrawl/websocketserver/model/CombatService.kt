package at.aau.hexabrawl.websocketserver.model

class CombatService {

    /**
     * Pure combat resolution – kein State-Mutation.
     * Aufrufen wenn attacker auf ein feindliches Feld zieht.
     * Voraussetzung: Adjacency-Check und Eigentumsprüfung bereits durch Move-Logik validiert.
     */
    fun resolveCombat(attacker: GameUnit, defender: GameUnit): CombatResult {
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

    fun applyCombatResult(result: CombatResult, attacker: GameUnit, defender: GameUnit, gameState: GameState)
    {
        if (!result.defenderSurvived) gameState.units.remove(defender)
        if (!result.attackerSurvived) {
            gameState.units.remove(attacker)
        } else {
            attacker.x = result.tileX
            attacker.y = result.tileY
        }
    }
}
