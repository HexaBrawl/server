package at.aau.hexabrawl.websocketserver.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class RoomRegistryTest {

    private lateinit var registry: RoomRegistry

    @BeforeEach
    fun setUp() {
        registry = RoomRegistry()
    }

    @Test
    fun `createRoom creates a valid room and stores it in both maps`() {
        val room = registry.createRoom(GameMode.DUAL_VALLEY)

        // Teste Room-Eigenschaften
        assertEquals(GameMode.DUAL_VALLEY, room.mode)
        assertEquals(6, room.joinCode.length)

        // Teste, ob der Raum über beide Maps gefunden wird
        assertEquals(room, registry.findById(room.roomId))
        assertEquals(room, registry.findByJoinCode(room.joinCode))
    }

    @Test
    fun `createRoom generates unique roomIds and joinCodes for multiple rooms`() {
        val room1 = registry.createRoom(GameMode.DUAL_VALLEY)
        val room2 = registry.createRoom(GameMode.DUAL_VALLEY)

        assertNotEquals(room1.roomId, room2.roomId)
        assertNotEquals(room1.joinCode, room2.joinCode)
        assertEquals(2, registry.getAllRooms().size)
    }

    @Test
    fun `getOpenRooms returns only rooms with WAITING_FOR_PLAYERS status`() {
        val openRoom = registry.createRoom(GameMode.DUAL_VALLEY)
        val inProgressRoom = registry.createRoom(GameMode.DUAL_VALLEY).apply {
            gameState.status = GameStatus.IN_PROGRESS
        }
        val finishedRoom = registry.createRoom(GameMode.DUAL_VALLEY).apply {
            gameState.status = GameStatus.FINISHED
        }

        val openRooms = registry.getOpenRooms()

        assertEquals(1, openRooms.size)
        assertTrue(openRooms.contains(openRoom))
        assertFalse(openRooms.contains(inProgressRoom))
        assertFalse(openRooms.contains(finishedRoom))
    }

    @Test
    fun `findById and findByJoinCode return null for unknown identifiers`() {
        assertNull(registry.findById("unknown-id"))
        assertNull(registry.findByJoinCode("XXXXXX"))
    }

    @Test
    fun `removeRoom completely removes room from both internal maps`() {
        val room = registry.createRoom(GameMode.DUAL_VALLEY)

        // Stelle sicher, dass der Raum vor dem Löschen da ist
        assertNotNull(registry.findById(room.roomId))
        assertNotNull(registry.findByJoinCode(room.joinCode))

        registry.removeRoom(room.roomId)

        // Teste, ob der Raum aus BEIDEN Maps (rooms & byJoinCode) entfernt wurde
        assertNull(registry.findById(room.roomId))
        assertNull(registry.findByJoinCode(room.joinCode))
        assertTrue(registry.getAllRooms().isEmpty())
    }

    @Test
    fun `removeRoom does nothing and throws no exception for unknown roomId`() {
        val room = registry.createRoom(GameMode.DUAL_VALLEY)

        // Aufruf trifft die `if (room != null)` Bedingung
        assertDoesNotThrow {
            registry.removeRoom("non-existent-id")
        }

        // Der existierende Raum darf davon nicht betroffen sein
        assertEquals(1, registry.getAllRooms().size)
    }

    @Test
    fun `getAllRooms returns empty list when registry is newly initialized`() {
        assertTrue(registry.getAllRooms().isEmpty())
    }

    @Test
    fun `room helper properties return correct values based on state and mode`() {
        // Deckt die Getter-Eigenschaften der `Room` data class ab
        val room = registry.createRoom(GameMode.TRIAD_OUTPOST)

        assertEquals(3, room.maxPlayers)
        assertEquals(GameStatus.WAITING_FOR_PLAYERS, room.status)

        room.gameState.players.add(Player("Alice", "s1", PlayerColor.RED))
        assertEquals(listOf("Alice"), room.players)
    }

    @Test
    fun `concurrent room creation is thread-safe`() {
        // Echter Lasttest für die Thread-Sicherheit der ConcurrentHashMaps
        val threadCount = 100
        val latch = CountDownLatch(threadCount)

        repeat(threadCount) {
            thread {
                registry.createRoom(GameMode.DUAL_VALLEY)
                latch.countDown()
            }
        }

        // Warte bis alle 100 Threads fertig sind
        latch.await()
        assertEquals(threadCount, registry.getAllRooms().size)
    }
}