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

    /**
     * Erstellt einen neuen Raum mit dem angegebenen [mode] und einer
     * kollisionsfreien 6-stelligen Join-Code. Gibt den erstellten [Room] zurück.
     */
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

    /** Gibt alle Räume zurück, die noch auf Spieler warten. */
    fun getOpenRooms(): List<Room> =
        rooms.values.filter { it.status == GameStatus.WAITING_FOR_PLAYERS }

    /**
     * Sucht einen Raum anhand seiner [roomId].
     * @return Den gefundenen [Room] oder null, wenn kein Raum mit dieser ID existiert.
     */
    fun findById(roomId: String): Room? = rooms[roomId]

    /**
     * Sucht einen Raum anhand des [joinCode].
     * @return Den gefundenen [Room] oder null, wenn kein Raum mit diesem Code existiert.
     */
    fun findByJoinCode(joinCode: String): Room? = byJoinCode[joinCode]

    /** Entfernt den Raum mit der angegebenen [roomId] aus beiden internen Maps. */
    fun removeRoom(roomId: String) {
        val room = rooms.remove(roomId)
        if (room != null) {
            byJoinCode.remove(room.joinCode)
        }
    }

    /** Gibt alle aktuell registrierten Räume zurück, unabhängig von ihrem Status. */
    fun getAllRooms(): List<Room> = rooms.values.toList()

    /** Generiert einen zufälligen 6-stelligen alphanumerischen Join-Code. */
    private fun generateJoinCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}


