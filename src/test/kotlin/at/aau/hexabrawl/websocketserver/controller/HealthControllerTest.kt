package at.aau.hexabrawl.websocketserver.controller

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

import at.aau.hexabrawl.websocketserver.model.GameService

class HealthControllerTest {

    private val gameService = GameService()
    private val controller = HealthController(gameService)

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
    fun `health endpoint returns game status`() {
        val response = controller.health()
        assertEquals("WAITING_FOR_PLAYERS", response.body?.get("gameStatus"))
    }

    @Test
    fun `health endpoint returns connected players count`() {
        val response = controller.health()
        assertEquals(0, response.body?.get("connectedPlayers"))
    }

    @Test
    fun `health endpoint returns max players`() {
        val response = controller.health()
        assertEquals(2, response.body?.get("maxPlayers"))
    }
}