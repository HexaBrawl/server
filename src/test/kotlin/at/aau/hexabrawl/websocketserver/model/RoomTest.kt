package at.aau.hexabrawl.websocketserver.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RoomTest {

    // ===== GameMode Tests =====

    @Test
    fun `DUAL_VALLEY has max 2 players`() {
        assertEquals(2, GameMode.DUAL_VALLEY.maxPlayers)
    }

    @Test
    fun `TRIAD_OUTPOST has max 3 players`() {
        assertEquals(3, GameMode.TRIAD_OUTPOST.maxPlayers)
    }

    @Test
    fun `BATTLEFIELD_PEAKS has max 4 players`() {
        assertEquals(4, GameMode.BATTLEFIELD_PEAKS.maxPlayers)
    }

    @Test
    fun `GameMode has exactly 3 values`() {
        assertEquals(3, GameMode.entries.size)
    }

    // ===== Room Tests =====

    @Test
    fun `Room is created with correct roomId and joinCode`() {
        val room = Room("room-1", "ABC123", GameMode.DUAL_VALLEY)
        assertEquals("room-1", room.roomId)
        assertEquals("ABC123", room.joinCode)
    }

    @Test
    fun `Room starts with WAITING_FOR_PLAYERS status`() {
        val room = Room("room-1", "ABC123", GameMode.DUAL_VALLEY)
        assertEquals(GameStatus.WAITING_FOR_PLAYERS, room.status)
    }

    @Test
    fun `Room starts with empty player list`() {
        val room = Room("room-1", "ABC123", GameMode.DUAL_VALLEY)
        assertTrue(room.players.isEmpty())
    }

    @Test
    fun `Room maxPlayers matches GameMode`() {
        val room = Room("room-1", "ABC123", GameMode.TRIAD_OUTPOST)
        assertEquals(3, room.maxPlayers)
    }

    @Test
    fun `Room players list reflects game state players`() {
        val room = Room("room-1", "ABC123", GameMode.DUAL_VALLEY)
        room.state.players.add(Player("Alice", "s1", PlayerColor.RED))
        assertEquals(listOf("Alice"), room.players)
    }

    @Test
    fun `Room status reflects game state status`() {
        val room = Room("room-1", "ABC123", GameMode.DUAL_VALLEY)
        room.state.status = GameStatus.IN_PROGRESS
        assertEquals(GameStatus.IN_PROGRESS, room.status)
    }
}