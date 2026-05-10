package at.aau.hexabrawl.websocketserver.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@RestController
class HealthController {

    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, Any>> {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        return ResponseEntity.ok(mapOf(
            "status" to "UP",
            "service" to "HexaBrawl Game Server",
            "version" to "1.0.0",
            "timestamp" to LocalDateTime.now().format(formatter)
        ))
    }
}