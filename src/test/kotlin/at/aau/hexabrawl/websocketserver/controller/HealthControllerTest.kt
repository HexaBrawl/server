package at.aau.hexabrawl.websocketserver.controller

import at.aau.hexabrawl.websocketserver.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class HealthControllerTest {

    private lateinit var registry: RoomRegistry
    private lateinit var controller: HealthController

    @BeforeEach
    fun setUp() {
        registry = RoomRegistry()
        controller = HealthController(registry)
    }

    @Test
    fun `health endpoint returns 200`() {
        val response = controller.health()
        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `health endpoint returns status UP`() {
        val response = controller.health()
        assertEquals("UP", response.body?.get("status"))
    }

    @Test
    fun `health endpoint returns service name`() {
        val response = controller.health()
        assertEquals("HexaBrawl Game Server", response.body?.get("service"))
    }

    @Test
    fun `health endpoint returns version`() {
        val response = controller.health()
        assertEquals("1.0.0", response.body?.get("version"))
    }

    @Test
    fun `health endpoint returns timestamp`() {
        val response = controller.health()
        assertNotNull(response.body?.get("timestamp"))
    }

    @Test
    fun `health endpoint returns empty rooms list when no rooms`() {
        val response = controller.health()
        @Suppress("UNCHECKED_CAST")
        val rooms = response.body?.get("rooms") as List<*>
        assertTrue(rooms.isEmpty())
    }

    @Test
    fun `health endpoint returns totalRooms as 0 when no rooms`() {
        val response = controller.health()
        assertEquals(0, response.body?.get("totalRooms"))
    }

    @Test
    fun `health endpoint returns totalPlayers as 0 when no rooms`() {
        val response = controller.health()
        assertEquals(0, response.body?.get("totalPlayers"))
    }

    @Test
    fun `health endpoint returns correct totalRooms`() {
        registry.createRoom(GameMode.DUAL_VALLEY)
        registry.createRoom(GameMode.TRIAD_OUTPOST)

        val response = controller.health()
        assertEquals(2, response.body?.get("totalRooms"))
    }

    @Test
    fun `health endpoint returns correct totalPlayers`() {
        val room = registry.createRoom(GameMode.DUAL_VALLEY)
        room.gameState.players.add(Player("Alice", "s1", PlayerColor.RED))
        room.gameState.players.add(Player("Bob", "s2", PlayerColor.BLUE))

        val response = controller.health()
        assertEquals(2, response.body?.get("totalPlayers"))
    }

    @Test
    fun `health endpoint rooms contain player names`() {
        val room = registry.createRoom(GameMode.DUAL_VALLEY)
        room.gameState.players.add(Player("Alice", "s1", PlayerColor.RED))

        val response = controller.health()
        @Suppress("UNCHECKED_CAST")
        val rooms = response.body?.get("rooms") as List<Map<String, Any>>
        val players = rooms[0]["players"] as List<*>

        assertTrue(players.contains("Alice"))
    }

    @Test
    fun `health endpoint rooms contain roomId`() {
        val room = registry.createRoom(GameMode.DUAL_VALLEY)

        val response = controller.health()
        @Suppress("UNCHECKED_CAST")
        val rooms = response.body?.get("rooms") as List<Map<String, Any>>

        assertEquals(room.roomId, rooms[0]["roomId"])
    }

    @Test
    fun `health endpoint rooms contain joinCode`() {
        val room = registry.createRoom(GameMode.DUAL_VALLEY)

        val response = controller.health()
        @Suppress("UNCHECKED_CAST")
        val rooms = response.body?.get("rooms") as List<Map<String, Any>>

        assertEquals(room.joinCode, rooms[0]["joinCode"])
    }

    @Test
    fun `health endpoint rooms contain maxPlayers`() {
        registry.createRoom(GameMode.TRIAD_OUTPOST)

        val response = controller.health()
        @Suppress("UNCHECKED_CAST")
        val rooms = response.body?.get("rooms") as List<Map<String, Any>>

        assertEquals(3, rooms[0]["maxPlayers"])
    }
}