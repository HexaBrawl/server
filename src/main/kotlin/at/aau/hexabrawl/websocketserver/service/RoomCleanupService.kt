package at.aau.hexabrawl.websocketserver.service

import at.aau.hexabrawl.websocketserver.model.GameStatus
import at.aau.hexabrawl.websocketserver.model.RoomRegistry
import org.slf4j.LoggerFactory
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * Service that automatically removes inactive rooms from the registry.
 * Runs periodically in the background to prevent server overload.
 * Broadcasts a closure notification to room subscribers when a room is removed.
 */
@Service
class RoomCleanupService(
    private val roomRegistry: RoomRegistry,
    private val messagingTemplate: SimpMessagingTemplate
) {

    private val roomCreationTimes = ConcurrentHashMap<String, LocalDateTime>()
    private val lock = Any()

    companion object {
        private val log = LoggerFactory.getLogger(RoomCleanupService::class.java)
        /** Time in minutes after which inactive rooms are removed. */
        const val CLEANUP_THRESHOLD_MINUTES = 5L
    }

    /**
     * Tracks the creation time of a room.
     * Should be called when a room is created.
     *
     * @param roomId The unique identifier of the room.
     * @param time The timestamp to register (Defaults to now, can be overridden for testing).
     */
    fun trackRoom(roomId: String, time: LocalDateTime = LocalDateTime.now()) = synchronized(lock) {
        roomCreationTimes[roomId] = time
    }

    /**
     * Removes rooms that are FINISHED or have no players after 5 minutes.
     * Broadcasts a closure notification to room subscribers before removing.
     * Runs automatically every minute in the background.
     */
    @Scheduled(fixedDelay = 60000)
    fun cleanupInactiveRooms() {
        // 1. Snapshot der IDs holen, die zeitlich abgelaufen sind (minimiert Lock-Dauer)
        val expiredRoomIds = synchronized(lock) {
            val threshold = LocalDateTime.now().minusMinutes(CLEANUP_THRESHOLD_MINUTES)
            roomCreationTimes.filter { it.value.isBefore(threshold) }.keys.toList()
        }

        // 2. Für jede abgelaufene ID den aktuellen Registry-Zustand prüfen und sicher löschen
        expiredRoomIds.forEach { roomId ->
            val room = roomRegistry.findById(roomId)

            // Verhindert Race Conditions: Nochmaliger Check des aktuellen Zustands kurz vor dem Löschen
            val shouldRemove = room == null ||
                    room.status == GameStatus.FINISHED ||
                    room.players.isEmpty()

            if (shouldRemove) {
                val destination = "/topic/rooms/$roomId/closed"
                val payload: Any = mapOf("roomId" to roomId, "reason" to "Room expired")
                messagingTemplate.convertAndSend(destination, payload)

                roomRegistry.removeRoom(roomId)

                synchronized(lock) {
                    roomCreationTimes.remove(roomId)
                }
                log.info("CleanupService: Room {} removed due to inactivity", roomId)
            }
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