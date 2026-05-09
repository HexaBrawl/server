package at.aau.hexabrawl.websocketserver.controller

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.assertj.MockMvcTester
import org.springframework.web.context.WebApplicationContext

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class HealthControllerTest {

    @Autowired
    private lateinit var context: WebApplicationContext

    private lateinit var mockMvc: MockMvcTester

    @BeforeEach
    fun setup() {
        mockMvc = MockMvcTester.from(context)
    }

    @Test
    fun `health endpoint returns 200`() {
        mockMvc.get().uri("/health").exchange()
            .assertThat()
            .hasStatusOk()
    }

    @Test
    fun `health endpoint returns status UP`() {
        mockMvc.get().uri("/health").exchange()
            .assertThat()
            .hasStatusOk()
            .bodyJson()
            .extractingPath("$.status").isEqualTo("UP")
    }
}