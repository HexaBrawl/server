package at.aau.hexabrawl.websocketserver.controller

import at.aau.hexabrawl.websocketserver.model.GameMode
import at.aau.hexabrawl.websocketserver.model.GameStatus
import at.aau.hexabrawl.websocketserver.model.RoomCleanupService
import at.aau.hexabrawl.websocketserver.model.RoomRegistry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.http.HttpStatus
import org.springframework.messaging.simp.SimpMessagingTemplate

class RoomControllerTest {

    private lateinit var registry: RoomRegistry
    private lateinit var cleanupService: RoomCleanupService
    private lateinit var controller: RoomController

    @BeforeEach
    fun setUp() {
        registry = RoomRegistry()
        val messagingTemplate = mock(SimpMessagingTemplate::class.java)
        cleanupService = RoomCleanupService(registry, messagingTemplate)
        controller = RoomController(registry, cleanupService)
    }

    // ===== POST /api/rooms =====

    @Test
    fun `createRoom returns 201`() {
        val response = controller.createRoom(GameMode.DUAL_VALLEY)
        assertEquals(HttpStatus.CREATED, response.statusCode)
    }

    @Test
    fun `createRoom returns room with correct mode`() {
        val response = controller.createRoom(GameMode.DUAL_VALLEY)
        assertEquals(GameMode.DUAL_VALLEY, response.body?.mode)
    }

    @Test
    fun `createRoom returns room with joinCode`() {
        val response = controller.createRoom(GameMode.DUAL_VALLEY)
        assertNotNull(response.body?.joinCode)
        assertEquals(6, response.body?.joinCode?.length)
    }

    @Test
    fun `createRoom returns room with correct maxPlayers`() {
        val response = controller.createRoom(GameMode.TRIAD_OUTPOST)
        assertEquals(3, response.body?.maxPlayers)
    }

    @Test
    fun `createRoom returns room with 0 current players`() {
        val response = controller.createRoom(GameMode.DUAL_VALLEY)
        assertEquals(0, response.body?.currentPlayers)
    }

    @Test
    fun `createRoom works for all game modes`() {
        val dual = controller.createRoom(GameMode.DUAL_VALLEY)
        val triad = controller.createRoom(GameMode.TRIAD_OUTPOST)
        val battle = controller.createRoom(GameMode.BATTLEFIELD_PEAKS)

        assertEquals(GameMode.DUAL_VALLEY, dual.body?.mode)
        assertEquals(GameMode.TRIAD_OUTPOST, triad.body?.mode)
        assertEquals(GameMode.BATTLEFIELD_PEAKS, battle.body?.mode)
    }

    // ===== GET /api/rooms =====

    @Test
    fun `getOpenRooms returns 200`() {
        val response = controller.getOpenRooms()
        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `getOpenRooms returns empty list when no rooms`() {
        val response = controller.getOpenRooms()
        assertTrue(response.body?.isEmpty() == true)
    }

    @Test
    fun `getOpenRooms returns only WAITING_FOR_PLAYERS rooms`() {
        controller.createRoom(GameMode.DUAL_VALLEY)
        val room2 = registry.createRoom(GameMode.DUAL_VALLEY)
        room2.gameState.status = GameStatus.IN_PROGRESS

        val response = controller.getOpenRooms()
        assertEquals(1, response.body?.size)
    }

    @Test
    fun `getOpenRooms returns all open rooms`() {
        controller.createRoom(GameMode.DUAL_VALLEY)
        controller.createRoom(GameMode.TRIAD_OUTPOST)

        val response = controller.getOpenRooms()
        assertEquals(2, response.body?.size)
    }

    // ===== GET /api/rooms/{roomId} =====

    @Test
    fun `getRoomById returns 200 for existing room`() {
        val created = controller.createRoom(GameMode.DUAL_VALLEY)
        val roomId = created.body?.roomId!!

        val response = controller.getRoomById(roomId)
        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `getRoomById returns correct room`() {
        val created = controller.createRoom(GameMode.DUAL_VALLEY)
        val roomId = created.body?.roomId!!

        val response = controller.getRoomById(roomId)
        assertEquals(roomId, response.body?.roomId)
    }

    @Test
    fun `getRoomById returns 404 for unknown roomId`() {
        val response = controller.getRoomById("unknown-id")
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `getRoomById returns correct joinCode`() {
        val created = controller.createRoom(GameMode.DUAL_VALLEY)
        val roomId = created.body?.roomId!!
        val joinCode = created.body?.joinCode!!

        val response = controller.getRoomById(roomId)
        assertEquals(joinCode, response.body?.joinCode)
    }

    // ===== GET /api/rooms/by-code/{joinCode} =====

    @Test
    fun `getRoomByJoinCode returns 200 for existing code`() {
        val created = controller.createRoom(GameMode.DUAL_VALLEY)
        val joinCode = created.body?.joinCode!!

        val response = controller.getRoomByJoinCode(joinCode)
        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `getRoomByJoinCode returns correct room`() {
        val created = controller.createRoom(GameMode.DUAL_VALLEY)
        val joinCode = created.body?.joinCode!!
        val roomId = created.body?.roomId!!

        val response = controller.getRoomByJoinCode(joinCode)
        assertEquals(roomId, response.body?.roomId)
        assertEquals(joinCode, response.body?.joinCode)
    }

    @Test
    fun `getRoomByJoinCode returns 404 for unknown code`() {
        val response = controller.getRoomByJoinCode("XXXXXX")
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    // ===== RoomDTO =====

    @Test
    fun `RoomDTO contains all required fields`() {
        val response = controller.createRoom(GameMode.DUAL_VALLEY)
        val dto = response.body!!

        assertNotNull(dto.roomId)
        assertNotNull(dto.joinCode)
        assertNotNull(dto.mode)
        assertNotNull(dto.maxPlayers)
        assertNotNull(dto.currentPlayers)
    }
}