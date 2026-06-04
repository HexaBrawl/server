package at.aau.hexabrawl.websocketserver.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.springframework.messaging.simp.SimpMessagingTemplate
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

class RoomCleanupServiceTest {

    private lateinit var registry: RoomRegistry
    private lateinit var cleanupService: RoomCleanupService
    private lateinit var messagingTemplate: SimpMessagingTemplate

    @BeforeEach
    fun setUp() {
        registry = RoomRegistry()
        messagingTemplate = mock(SimpMessagingTemplate::class.java)
        cleanupService = RoomCleanupService(registry, messagingTemplate)
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

        setRoomAge(room.roomId)

        cleanupService.cleanupInactiveRooms()

        assertNull(registry.findById(room.roomId))
        assertEquals(0, cleanupService.getTrackedRoomCount())
    }

    @Test
    fun `cleanupInactiveRooms removes empty rooms after threshold`() {
        val room = registry.createRoom(GameMode.DUAL_VALLEY)

        setRoomAge(room.roomId)

        cleanupService.cleanupInactiveRooms()

        assertNull(registry.findById(room.roomId))
    }

    @Test
    fun `cleanupInactiveRooms keeps active rooms with players`() {
        val room = registry.createRoom(GameMode.DUAL_VALLEY)
        room.gameState.players.add(Player("Alice", "s1", PlayerColor.RED))
        room.gameState.status = GameStatus.IN_PROGRESS

        setRoomAge(room.roomId)

        cleanupService.cleanupInactiveRooms()

        assertNotNull(registry.findById(room.roomId))
    }

    @Test
    fun `getTrackedRoomCount returns 0 when no rooms tracked`() {
        assertEquals(0, cleanupService.getTrackedRoomCount())
    }

    @Test
    fun `cleanupInactiveRooms handles unknown roomId gracefully`() {
        setRoomAge("non-existent-id")

        assertDoesNotThrow { cleanupService.cleanupInactiveRooms() }
        assertEquals(0, cleanupService.getTrackedRoomCount())
    }

    @Test
    fun `cleanupInactiveRooms broadcasts closed event before removing room`() {
        val room = registry.createRoom(GameMode.DUAL_VALLEY)
        room.gameState.status = GameStatus.FINISHED

        setRoomAge(room.roomId)

        cleanupService.cleanupInactiveRooms()

        verify(messagingTemplate).convertAndSend(
            eq("/topic/rooms/${room.roomId}/closed"),
            any(Any::class.java)
        )
    }

    @Test
    fun `cleanupInactiveRooms does not broadcast for fresh rooms`() {
        val room = registry.createRoom(GameMode.DUAL_VALLEY)
        cleanupService.trackRoom(room.roomId)

        cleanupService.cleanupInactiveRooms()

        verifyNoInteractions(messagingTemplate)
    }

    // Helper Funktion
    private fun setRoomAge(roomId: String) {
        val field = RoomCleanupService::class.java.getDeclaredField("roomCreationTimes")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val times = field.get(cleanupService) as ConcurrentHashMap<String, LocalDateTime>
        times[roomId] = LocalDateTime.now().minusMinutes(6)
    }
}