package at.aau.hexabrawl.websocketserver.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HexDistanceTest {

    @Test
    fun `distance to same cell is zero`() {
        assertEquals(0, HexDistance.between(3, 3, 3, 3))
    }

    @Test
    fun `distance to horizontal neighbor is one`() {
        assertEquals(1, HexDistance.between(3, 3, 4, 3))
        assertEquals(1, HexDistance.between(4, 3, 3, 3))
    }

    @Test
    fun `distance to vertical neighbor is one`() {
        assertEquals(1, HexDistance.between(3, 3, 3, 4))
        assertEquals(1, HexDistance.between(3, 4, 3, 3))
    }

    @Test
    fun `distance two horizontal hexes is two`() {
        assertEquals(2, HexDistance.between(3, 3, 5, 3))
    }

    @Test
    fun `distance three horizontal hexes is three`() {
        assertEquals(3, HexDistance.between(3, 3, 6, 3))
    }

    @Test
    fun `distance is symmetric`() {
        val ab = HexDistance.between(2, 2, 5, 5)
        val ba = HexDistance.between(5, 5, 2, 2)
        assertEquals(ab, ba)
    }

    @Test
    fun `distance from origin to far corner`() {
        // 8x8 Grid: (0,0) -> (7,7) sollte > 2 sein
        val distance = HexDistance.between(0, 0, 7, 7)
        assertTrue(distance > 2, "Erwartet > 2, war $distance")
    }
}
