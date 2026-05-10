package at.aau.hexabrawl.websocketserver.controller

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class HealthControllerTest {

    private val controller = HealthController()

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
    fun `health endpoint returns timestamp`() {
        val response = controller.health()
        assertNotNull(response.body?.get("timestamp"))
    }

    @Test
    fun `health endpoint returns version`() {
        val response = controller.health()
        assertEquals("1.0.0", response.body?.get("version"))
    }
}