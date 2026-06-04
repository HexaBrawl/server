package at.aau.hexabrawl.websocketserver.model

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * Service that automatically removes inactive rooms from the registry.
 * Runs periodically in the background to prevent server overload.
 */
@Service
class RoomCleanupService(
    private val roomRegistry: RoomRegistry
) {

    private val roomCreationTimes = java.util.concurrent.ConcurrentHashMap<String, LocalDateTime>()
    private val lock = Any()

    companion object {
        /** Time in minutes after which inactive rooms are removed. */
        const val CLEANUP_THRESHOLD_MINUTES = 5L
    }

    /**
     * Tracks the creation time of a room.
     * Should be called when a room is created.
     *
     * @param roomId The unique identifier of the room.
     */
    fun trackRoom(roomId: String) = synchronized(lock) {
        roomCreationTimes[roomId] = LocalDateTime.now()
    }

    /**
     * Removes rooms that are FINISHED or have no players after 5 minutes.
     * Runs automatically every minute in the background.
     */
    @Scheduled(fixedDelay = 60000)
    fun cleanupInactiveRooms() = synchronized(lock) {
        val threshold = LocalDateTime.now().minusMinutes(CLEANUP_THRESHOLD_MINUTES)

        val roomsToRemove = roomCreationTimes.filter { (roomId, createdAt) ->
            if (createdAt.isBefore(threshold)) {
                val room = roomRegistry.findById(roomId)
                room == null ||
                        room.status == GameStatus.FINISHED ||
                        room.players.isEmpty()
            } else false
        }.keys

        roomsToRemove.forEach { roomId ->
            roomRegistry.removeRoom(roomId)
            roomCreationTimes.remove(roomId)
            println("CleanupService: Room $roomId removed")
        }
    }

    /**
     * Returns the number of currently tracked rooms.
     *
     * @return Number of tracked rooms.
     */
    fun getTrackedRoomCount(): Int = synchronized(lock) {
        roomCreationTimes.size
    }
}