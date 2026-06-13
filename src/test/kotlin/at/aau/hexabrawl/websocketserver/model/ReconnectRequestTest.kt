package at.aau.hexabrawl.websocketserver.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ReconnectRequestTest {

    @Test
    fun `reconnect request has empty defaults`() {
        val request = ReconnectRequest()
        assertEquals("", request.playerName)
        assertEquals("", request.joinCode)
    }

    @Test
    fun `reconnect request exposes constructor arguments`() {
        val request = ReconnectRequest(playerName = "Alice", joinCode = "ABC123")
        assertEquals("Alice", request.playerName)
        assertEquals("ABC123", request.joinCode)
    }
}
