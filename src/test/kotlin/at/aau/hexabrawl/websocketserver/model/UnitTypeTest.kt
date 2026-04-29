package at.aau.hexabrawl.websocketserver.model
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class UnitTypeTest {

    @Test
    fun `unit type enum contains all expected values`() {
        val types = UnitType.values().toList()

        assertTrue(types.contains(UnitType.ARCHER))
        assertTrue(types.contains(UnitType.INFANTRY))
        assertTrue(types.contains(UnitType.CAVALRY))
    }
}