package at.aau.hexabrawl.websocketserver.controller

import at.aau.hexabrawl.websocketserver.model.GameMode
import at.aau.hexabrawl.websocketserver.model.RoomRegistry
import at.aau.hexabrawl.websocketserver.service.RoomCleanupService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * REST Controller for managing game rooms.
 * Provides endpoints to create and retrieve game rooms.
 */
@RestController
@RequestMapping("/api/rooms")
class RoomController(private val roomRegistry: RoomRegistry, private val cleanupService: RoomCleanupService) {

    /**
     * POST /api/rooms
     * Creates a new game room with the specified game mode.
     *
     * @param mode The game mode for the new room (default: DUAL_VALLEY).
     * @return The created room as DTO with status 201.
     */
    @PostMapping
    fun createRoom(
        @RequestParam(defaultValue = "DUAL_VALLEY") mode: GameMode
    ): ResponseEntity<RoomDTO> {
        val room = roomRegistry.createRoom(mode)
        cleanupService.trackRoom(room.roomId)  // ← hinzufügen
        return ResponseEntity.status(201).body(room.toDTO())
    }

    /**
     * GET /api/rooms
     * Returns all rooms currently waiting for players.
     *
     * @return List of open rooms as DTOs.
     */
    @GetMapping
    fun getOpenRooms(): ResponseEntity<List<RoomDTO>> {
        val openRooms = roomRegistry.getOpenRooms().map { it.toDTO() }
        return ResponseEntity.ok(openRooms)
    }

    /**
     * GET /api/rooms/{roomId}
     * Returns a single room by its unique roomId.
     *
     * @param roomId The unique identifier of the room.
     * @return The room as DTO or 404 if not found.
     */
    @GetMapping("/{roomId}")
    fun getRoomById(@PathVariable roomId: String): ResponseEntity<RoomDTO> {
        val room = roomRegistry.findById(roomId)
        return if (room != null) {
            ResponseEntity.ok(room.toDTO())
        } else {
            ResponseEntity.notFound().build()
        }
    }

    /**
     * GET /api/rooms/by-code/{joinCode}
     * Returns a single room by its 6-character join code.
     *
     * @param joinCode The short join code shared between players.
     * @return The room as DTO or 404 if not found.
     */
    @GetMapping("/by-code/{joinCode}")
    fun getRoomByJoinCode(@PathVariable joinCode: String): ResponseEntity<RoomDTO> {
        val room = roomRegistry.findByJoinCode(joinCode)
        return if (room != null) {
            ResponseEntity.ok(room.toDTO())
        } else {
            ResponseEntity.notFound().build()
        }
    }
}