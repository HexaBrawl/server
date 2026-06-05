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
    private val byJoinCode = ConcurrentHashMap<String, Room>()

    fun createRoom(mode: GameMode): Room {
        val roomId = UUID.randomUUID().toString()
        while (true) {
            val joinCode = generateJoinCode()
            val room = Room(roomId, joinCode, mode, GameState(gameMode = mode))
            if (byJoinCode.putIfAbsent(joinCode, room) == null) {
                rooms[roomId] = room
                return room
            }
        }
    }

    fun getOpenRooms(): List<Room> =
        rooms.values.filter { it.status == GameStatus.WAITING_FOR_PLAYERS }

    fun findById(roomId: String): Room? = rooms[roomId]

    fun findByJoinCode(joinCode: String): Room? = byJoinCode[joinCode]

    fun removeRoom(roomId: String) {
        val room = rooms.remove(roomId)
        if (room != null) {
            byJoinCode.remove(room.joinCode)
        }
    }

    fun getAllRooms(): List<Room> = rooms.values.toList()

    private fun generateJoinCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}


