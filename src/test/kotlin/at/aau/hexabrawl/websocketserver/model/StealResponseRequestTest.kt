package at.aau.hexabrawl.websocketserver.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StealResponseRequestTest {

    @Test
    fun `steal response request has empty defaults`() {
        val request = StealResponseRequest()
        assertEquals("", request.playerName)
        assertFalse(request.accept)
    }

    @Test
    fun `steal response request exposes constructor arguments`() {
        val request = StealResponseRequest(playerName = "Bob", accept = true)
        assertEquals("Bob", request.playerName)
        assertTrue(request.accept)
    }
}
