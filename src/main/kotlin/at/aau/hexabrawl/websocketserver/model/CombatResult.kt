
package at.aau.hexabrawl.websocketserver.model

data class CombatResult(
    val attackerSurvived: Boolean,
    val defenderSurvived: Boolean,
    val winnerId: String?,
    val tileX: Int,
    val tileY: Int
    // TODO: Economy-Hook – wird in Wirtschafts-Issue implementiert
)