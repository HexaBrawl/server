package at.aau.hexabrawl.websocketserver.controller

import at.aau.hexabrawl.websocketserver.model.GameMode
import at.aau.hexabrawl.websocketserver.model.Room
import at.aau.hexabrawl.websocketserver.model.RoomRegistry
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * REST Controller for managing game rooms.
 * Provides endpoints to create and retrieve game rooms.
 */
@RestController
@RequestMapping("/api/rooms")
class RoomController(private val roomRegistry: RoomRegistry) {

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
}

/**
 * Data Transfer Object for Room responses.
 * Prevents serialization issues with internal GameState lock.
 *
 * @property roomId Unique identifier of the room.
 * @property joinCode 6-character code for joining the room.
 * @property mode The game mode of the room.
 * @property maxPlayers Maximum number of players allowed.
 * @property currentPlayers Current number of players in the room.
 */
data class RoomDTO(
    val roomId: String,
    val joinCode: String,
    val mode: GameMode,
    val maxPlayers: Int,
    val currentPlayers: Int
)

/**
 * Extension function to convert a Room to a RoomDTO.
 *
 * @return RoomDTO representation of this room.
 */
fun Room.toDTO(): RoomDTO {
    return RoomDTO(
        roomId = this.roomId,
        joinCode = this.joinCode,
        mode = this.mode,
        maxPlayers = this.maxPlayers,
        currentPlayers = this.players.size
    )
}