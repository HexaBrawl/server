package at.aau.hexabrawl.websocketserver.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CombatServiceTest {

    private lateinit var combatService: CombatService
    private lateinit var gameState: GameState

    @BeforeEach
    fun setup() {
        combatService = CombatService()
        gameState = GameState().apply {
            players.addAll(listOf(Player("Alice"), Player("Bob")))
            status = GameStatus.IN_PROGRESS
        }
    }

    // Angreifer gewinnt
    @Test fun infantry_beats_cavalry() = assertAttackerWins(UnitType.INFANTRY, UnitType.CAVALRY)
    @Test fun cavalry_beats_archer()   = assertAttackerWins(UnitType.CAVALRY,  UnitType.ARCHER)
    @Test fun archer_beats_infantry()  = assertAttackerWins(UnitType.ARCHER,   UnitType.INFANTRY)

    // Verteidiger gewinnt
    @Test fun cavalry_loses_against_infantry() = assertDefenderWins(UnitType.CAVALRY,  UnitType.INFANTRY)
    @Test fun archer_loses_against_cavalry()   = assertDefenderWins(UnitType.ARCHER,   UnitType.CAVALRY)
    @Test fun infantry_loses_against_archer()  = assertDefenderWins(UnitType.INFANTRY, UnitType.ARCHER)

    // Gleichstand
    @Test fun infantry_vs_infantry_is_draw() = assertDraw(UnitType.INFANTRY)
    @Test fun cavalry_vs_cavalry_is_draw()   = assertDraw(UnitType.CAVALRY)
    @Test fun archer_vs_archer_is_draw()     = assertDraw(UnitType.ARCHER)

    // applyCombatResult
    @Test
    fun attacker_moves_to_defender_tile_on_win() {
        val attacker = GameUnit("Alice", 0, 0, UnitType.INFANTRY)
        val defender = GameUnit("Bob",   3, 3, UnitType.CAVALRY)
        gameState.units.addAll(listOf(attacker, defender))

        val result = combatService.resolveCombat(attacker, defender)
        combatService.applyCombatResult(result, attacker, defender)

        assertEquals(3, attacker.x)
        assertEquals(3, attacker.y)
    }

    @Test
    fun attacker_does_not_move_on_draw() {
        val attacker = GameUnit("Alice", 0, 0, UnitType.INFANTRY)
        val defender = GameUnit("Bob",   3, 3, UnitType.INFANTRY)
        gameState.units.addAll(listOf(attacker, defender))

        val result = combatService.resolveCombat(attacker, defender)
        combatService.applyCombatResult(result, attacker, defender)

        assertEquals(0, attacker.x)
        assertEquals(0, attacker.y)
    }

    @Test
    fun attacker_does_not_move_on_defender_win() {
        val attacker = GameUnit("Alice", 0, 0, UnitType.CAVALRY)
        val defender = GameUnit("Bob",   3, 3, UnitType.INFANTRY)
        gameState.units.addAll(listOf(attacker, defender))

        val result = combatService.resolveCombat(attacker, defender)
        combatService.applyCombatResult(result, attacker, defender)

        assertEquals(0, attacker.x)
        assertEquals(0, attacker.y)
        assertEquals(3, defender.x)
        assertEquals(3, defender.y)
    }

    // Hilfsmethoden
    private fun assertAttackerWins(attackerType: UnitType, defenderType: UnitType) {
        val result = combatService.resolveCombat(
            GameUnit("Alice", 0, 0, attackerType),
            GameUnit("Bob",   1, 0, defenderType)
        )
        assertTrue(result.attackerSurvived)
        assertFalse(result.defenderSurvived)
        assertEquals("Alice", result.winnerId)
    }

    private fun assertDefenderWins(attackerType: UnitType, defenderType: UnitType) {
        val result = combatService.resolveCombat(
            GameUnit("Alice", 0, 0, attackerType),
            GameUnit("Bob",   1, 0, defenderType)
        )
        assertFalse(result.attackerSurvived)
        assertTrue(result.defenderSurvived)
        assertEquals("Bob", result.winnerId)
    }

    private fun assertDraw(type: UnitType) {
        val result = combatService.resolveCombat(
            GameUnit("Alice", 0, 0, type),
            GameUnit("Bob",   1, 0, type)
        )
        assertFalse(result.attackerSurvived)
        assertFalse(result.defenderSurvived)
        assertNull(result.winnerId)
    }
}