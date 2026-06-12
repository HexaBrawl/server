package at.aau.hexabrawl.websocketserver.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ClaimGiftRequestTest {

    @Test
    fun `claim gift request has empty defaults`() {
        val request = ClaimGiftRequest()
        assertEquals("", request.playerName)
        assertEquals(0, request.delta)
    }

    @Test
    fun `claim gift request exposes constructor arguments`() {
        val request = ClaimGiftRequest(playerName = "Alice", delta = 7)
        assertEquals("Alice", request.playerName)
        assertEquals(7, request.delta)
    }
}
