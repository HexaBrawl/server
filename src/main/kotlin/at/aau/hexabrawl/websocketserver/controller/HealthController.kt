package at.aau.hexabrawl.websocketserver.controller

import at.aau.hexabrawl.websocketserver.model.GameStatus
import at.aau.hexabrawl.websocketserver.model.RoomRegistry
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * REST Controller for server health information.
 * Provides server status and active room information.
 */
@RestController
class HealthController(
    private val roomRegistry: RoomRegistry
) {

    private val startTime = LocalDateTime.now()

    /**
     * GET /health
     * Returns server health status including all active rooms and players.
     *
     * @return Health information including rooms, player counts and uptime.
     */
    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, Any>> {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val allRooms = roomRegistry.getAllRooms()

        val roomInfos = allRooms.map { room ->
            mapOf(
                "roomId" to room.roomId,
                "joinCode" to room.joinCode,
                "mode" to room.mode,
                "gameStatus" to room.status,
                "players" to room.players,
                "maxPlayers" to room.maxPlayers
            )
        }

        return ResponseEntity.ok(mapOf(
            "status" to "UP",
            "service" to "HexaBrawl Game Server",
            "version" to "1.0.0",
            "timestamp" to LocalDateTime.now().format(formatter),
            "uptime" to java.time.Duration.between(startTime, LocalDateTime.now()).seconds.toString() + "s",
            "rooms" to roomInfos,
            "totalRooms" to allRooms.size,
            "totalPlayers" to allRooms.sumOf { it.players.size },
            "openRooms" to allRooms.count { it.status == GameStatus.WAITING_FOR_PLAYERS },
            "activeRooms" to allRooms.count { it.status == GameStatus.IN_PROGRESS }
        ))
    }
}