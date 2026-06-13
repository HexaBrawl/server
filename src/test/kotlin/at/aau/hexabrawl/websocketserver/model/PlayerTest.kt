package at.aau.hexabrawl.websocketserver.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper

class PlayerTest {
    @Test
    fun `player serializes and deserializes income and upkeep`() {
        val mapper = ObjectMapper()
        val original = Player(name = "Alice", income = 7, upkeep = 4)
        val json = mapper.writeValueAsString(original)
        val parsed = mapper.readValue(json, Player::class.java)
        assertEquals(7, parsed.income)
        assertEquals(4, parsed.upkeep)
    }

    @Test
    fun `new player has income and upkeep zero by default`() {
        val player = Player(name = "Alice")
        assertEquals(0, player.income)
        assertEquals(0, player.upkeep)
    }

    @Test
    fun `new player has hasUsedGift false by default`() {
        val player = Player(name = "Alice")
        assertFalse(player.hasUsedGift)
    }

    @Test
    fun `hasUsedGift can be toggled at runtime`() {
        val player = Player(name = "Alice")
        player.hasUsedGift = true
        assertTrue(player.hasUsedGift)
    }

    @Test
    fun `new player is connected by default`() {
        val player = Player(name = "Alice")
        assertTrue(player.connected)
    }

    @Test
    fun `new player has disconnectedAt null by default`() {
        val player = Player(name = "Alice")
        assertNull(player.disconnectedAt)
    }

    @Test
    fun `connected and disconnectedAt can be toggled at runtime`() {
        val player = Player(name = "Alice")
        player.connected = false
        player.disconnectedAt = 1_700_000_000_000L
        assertFalse(player.connected)
        assertEquals(1_700_000_000_000L, player.disconnectedAt)
    }

    @Test
    fun `sessionId can be reassigned at runtime`() {
        val player = Player(name = "Alice", sessionId = "session-old")
        player.sessionId = "session-new"
        assertEquals("session-new", player.sessionId)
    }
}