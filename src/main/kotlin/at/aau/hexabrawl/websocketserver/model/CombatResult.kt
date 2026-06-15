
package at.aau.hexabrawl.websocketserver.model

/**
 * Ergebnis eines Combat-Auflösungs-Schritts aus
 * [at.aau.hexabrawl.websocketserver.service.CombatService.resolveCombat].
 *
 * Beide Survived-Flags koennen `true` (nichts passiert nach Combat —
 * derzeit aber nicht erreichbar) oder beide `false` (Patt: beide
 * sterben) sein. [winnerId] ist `null` bei Patt.
 */
data class CombatResult(
    val attackerSurvived: Boolean,
    val defenderSurvived: Boolean,
    val winnerId: String?,
    val tileX: Int,
    val tileY: Int
    // TODO: Economy-Hook – wird in Wirtschafts-Issue implementiert
)