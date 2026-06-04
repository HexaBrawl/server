package at.aau.hexabrawl.websocketserver.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RoomCleanupServiceTest {

    private lateinit var registry: RoomRegistry
    private lateinit var cleanupService: RoomCleanupService

    @BeforeEach
    fun setUp() {
        registry = RoomRegistry()
        cleanupService = RoomCleanupService(registry)
    }

    @Test
    fun `trackRoom adds room to tracked rooms`() {
        val room = registry.createRoom(GameMode.DUAL_VALLEY)
        cleanupService.trackRoom(room.roomId)
        assertEquals(1, cleanupService.getTrackedRoomCount())
    }

    @Test
    fun `trackRoom tracks multiple rooms`() {
        val room1 = registry.createRoom(GameMode.DUAL_VALLEY)
        val room2 = registry.createRoom(GameMode.TRIAD_OUTPOST)
        cleanupService.trackRoom(room1.roomId)
        cleanupService.trackRoom(room2.roomId)
        assertEquals(2, cleanupService.getTrackedRoomCount())
    }

    @Test
    fun `cleanupInactiveRooms does not remove fresh rooms`() {
        val room = registry.createRoom(GameMode.DUAL_VALLEY)
        cleanupService.trackRoom(room.roomId)

        cleanupService.cleanupInactiveRooms()

        assertNotNull(registry.findById(room.roomId))
        assertEquals(1, cleanupService.getTrackedRoomCount())
    }

    @Test
    fun `cleanupInactiveRooms removes FINISHED rooms after threshold`() {
        val room = registry.createRoom(GameMode.DUAL_VALLEY)
        room.gameState.status = GameStatus.FINISHED

        // Simuliere alten Zeitstempel über Reflection
        val field = RoomCleanupService::class.java.getDeclaredField("roomCreationTimes")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val times = field.get(cleanupService) as java.util.concurrent.ConcurrentHashMap<String, java.time.LocalDateTime>
        times[room.roomId] = java.time.LocalDateTime.now().minusMinutes(6)

        cleanupService.cleanupInactiveRooms()

        assertNull(registry.findById(room.roomId))
        assertEquals(0, cleanupService.getTrackedRoomCount())
    }

    @Test
    fun `cleanupInactiveRooms removes empty rooms after threshold`() {
        val room = registry.createRoom(GameMode.DUAL_VALLEY)

        val field = RoomCleanupService::class.java.getDeclaredField("roomCreationTimes")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val times = field.get(cleanupService) as java.util.concurrent.ConcurrentHashMap<String, java.time.LocalDateTime>
        times[room.roomId] = java.time.LocalDateTime.now().minusMinutes(6)

        cleanupService.cleanupInactiveRooms()

        assertNull(registry.findById(room.roomId))
    }

    @Test
    fun `cleanupInactiveRooms keeps active rooms with players`() {
        val room = registry.createRoom(GameMode.DUAL_VALLEY)
        room.gameState.players.add(Player("Alice", "s1", PlayerColor.RED))
        room.gameState.status = GameStatus.IN_PROGRESS

        val field = RoomCleanupService::class.java.getDeclaredField("roomCreationTimes")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val times = field.get(cleanupService) as java.util.concurrent.ConcurrentHashMap<String, java.time.LocalDateTime>
        times[room.roomId] = java.time.LocalDateTime.now().minusMinutes(6)

        cleanupService.cleanupInactiveRooms()

        assertNotNull(registry.findById(room.roomId))
    }

    @Test
    fun `getTrackedRoomCount returns 0 when no rooms tracked`() {
        assertEquals(0, cleanupService.getTrackedRoomCount())
    }

    @Test
    fun `cleanupInactiveRooms handles unknown roomId gracefully`() {
        val field = RoomCleanupService::class.java.getDeclaredField("roomCreationTimes")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val times = field.get(cleanupService) as java.util.concurrent.ConcurrentHashMap<String, java.time.LocalDateTime>
        times["non-existent-id"] = java.time.LocalDateTime.now().minusMinutes(6)

        assertDoesNotThrow { cleanupService.cleanupInactiveRooms() }
        assertEquals(0, cleanupService.getTrackedRoomCount())
    }
}