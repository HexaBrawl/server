package at.aau.hexabrawl.websocketserver.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RoomRegistryTest {

    private lateinit var registry: RoomRegistry

    @BeforeEach
    fun setUp() {
        registry = RoomRegistry()
    }

    @Test
    fun `createRoom returns room with correct mode`() {
        val room = registry.createRoom(GameMode.DUAL_VALLEY)
        assertEquals(GameMode.DUAL_VALLEY, room.mode)
    }

    @Test
    fun `createRoom generates unique roomIds`() {
        val room1 = registry.createRoom(GameMode.DUAL_VALLEY)
        val room2 = registry.createRoom(GameMode.DUAL_VALLEY)
        assertNotEquals(room1.roomId, room2.roomId)
    }

    @Test
    fun `createRoom generates 6 character joinCode`() {
        val room = registry.createRoom(GameMode.DUAL_VALLEY)
        assertEquals(6, room.joinCode.length)
    }

    @Test
    fun `createRoom adds room to registry`() {
        val room = registry.createRoom(GameMode.DUAL_VALLEY)
        assertNotNull(registry.findById(room.roomId))
    }


    @Test
    fun `getOpenRooms returns only WAITING_FOR_PLAYERS rooms`() {
        val room1 = registry.createRoom(GameMode.DUAL_VALLEY)
        val room2 = registry.createRoom(GameMode.DUAL_VALLEY)
        room2.gameState.status = GameStatus.IN_PROGRESS

        val openRooms = registry.getOpenRooms()
        assertTrue(openRooms.contains(room1))
        assertFalse(openRooms.contains(room2))
    }

    @Test
    fun `getOpenRooms returns empty list when no open rooms`() {
        val room = registry.createRoom(GameMode.DUAL_VALLEY)
        room.gameState.status = GameStatus.FINISHED
        assertTrue(registry.getOpenRooms().isEmpty())
    }

    @Test
    fun `findById returns correct room`() {
        val room = registry.createRoom(GameMode.DUAL_VALLEY)
        assertEquals(room, registry.findById(room.roomId))
    }

    @Test
    fun `findById returns null for unknown roomId`() {
        assertNull(registry.findById("unknown-id"))
    }

    @Test
    fun `findByJoinCode returns correct room`() {
        val room = registry.createRoom(GameMode.DUAL_VALLEY)
        assertEquals(room, registry.findByJoinCode(room.joinCode))
    }

    @Test
    fun `findByJoinCode returns null for unknown joinCode`() {
        assertNull(registry.findByJoinCode("XXXXXX"))
    }

    @Test
    fun `removeRoom removes room from registry`() {
        val room = registry.createRoom(GameMode.DUAL_VALLEY)
        registry.removeRoom(room.roomId)
        assertNull(registry.findById(room.roomId))
    }

    @Test
    fun `getAllRooms returns all rooms`() {
        registry.createRoom(GameMode.DUAL_VALLEY)
        registry.createRoom(GameMode.TRIAD_OUTPOST)
        registry.createRoom(GameMode.BATTLEFIELD_PEAKS)
        assertEquals(3, registry.getAllRooms().size)
    }

    @Test
    fun `getAllRooms returns empty list when no rooms`() {
        assertTrue(registry.getAllRooms().isEmpty())
    }

    @Test
    fun `registry supports 40 parallel rooms`() {
        repeat(40) { registry.createRoom(GameMode.DUAL_VALLEY) }
        assertEquals(40, registry.getAllRooms().size)
    }

    @Test
    fun `createRoom works for all game modes`() {
        val dual = registry.createRoom(GameMode.DUAL_VALLEY)
        val triad = registry.createRoom(GameMode.TRIAD_OUTPOST)
        val battle = registry.createRoom(GameMode.BATTLEFIELD_PEAKS)

        assertEquals(GameMode.DUAL_VALLEY, dual.mode)
        assertEquals(GameMode.TRIAD_OUTPOST, triad.mode)
        assertEquals(GameMode.BATTLEFIELD_PEAKS, battle.mode)
    }

    @Test
    fun `room maxPlayers matches game mode`() {
        val room = registry.createRoom(GameMode.TRIAD_OUTPOST)
        assertEquals(3, room.maxPlayers)
    }

    @Test
    fun `room players list reflects game state`() {
        val room = registry.createRoom(GameMode.DUAL_VALLEY)
        room.gameState.players.add(Player("Alice", "s1", PlayerColor.RED))
        assertEquals(listOf("Alice"), room.players)
    }

    @Test
    fun `findByJoinCode returns null when registry is empty`() {
        val result = registry.findByJoinCode("AAAAAA")
        assertNull(result)
    }

    @Test
    fun `findByJoinCode returns null when room exists but code does not match`() {
        registry.createRoom(GameMode.DUAL_VALLEY) // Raum existiert
        assertNull(registry.findByJoinCode("ZZZZZZ")) // aber falscher Code
    }



}