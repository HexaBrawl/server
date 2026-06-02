package at.aau.hexabrawl.websocketserver.model

import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry that manages all active game rooms on the server.
 * Uses ConcurrentHashMap for thread-safe parallel access without explicit locking.
 */
@Component
class RoomRegistry {

    private val rooms = ConcurrentHashMap<String, Room>()

    /**
     * Creates a new room with a unique roomId and joinCode.
     *
     * @param mode The game mode for the new room.
     * @return The newly created room.
     */
    fun createRoom(mode: GameMode): Room {
        val roomId = UUID.randomUUID().toString()
        val joinCode = generateJoinCode()
        val room = Room(roomId, joinCode, mode)
        rooms[roomId] = room
        return room
    }

    /**
     * Returns all rooms that are currently waiting for players.
     *
     * @return List of open rooms.
     */
    fun getOpenRooms(): List<Room> {
        return rooms.values.filter { it.status == GameStatus.WAITING_FOR_PLAYERS }
    }

    /**
     * Finds a room by its unique roomId.
     *
     * @param roomId The unique identifier of the room.
     * @return The room if found, null otherwise.
     */
    fun findById(roomId: String): Room? {
        return rooms[roomId]
    }

    /**
     * Finds a room by its join code.
     *
     * @param joinCode The 6-character join code.
     * @return The room if found, null otherwise.
     */
    fun findByJoinCode(joinCode: String): Room? {
        return rooms.values.find { it.joinCode == joinCode }
    }

    /**
     * Removes a room from the registry.
     *
     * @param roomId The unique identifier of the room to remove.
     */
    fun removeRoom(roomId: String) {
        rooms.remove(roomId)
    }

    /**
     * Returns all rooms regardless of status.
     *
     * @return List of all rooms.
     */
    fun getAllRooms(): List<Room> {
        return rooms.values.toList()
    }

    /**
     * Generates a unique 6-character alphanumeric join code.
     *
     * @return A unique 6-character join code.
     */
    private fun generateJoinCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}