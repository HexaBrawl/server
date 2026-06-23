package at.aau.hexabrawl.websocketserver.service

import at.aau.hexabrawl.websocketserver.model.RoomRegistry
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Scheduled Cleanup-Task fuer den Reconnect-Flow.
 *
 * Laeuft alle 5 Sekunden ueber alle Rooms und ruft [PlayerService.hardDelete]
 * fuer Spieler auf, deren Soft-Disconnect laenger als [GRACE_PERIOD_MS] zurueckliegt.
 *
 * Nach dem Cleanup wird der aktuelle GameState an die Room-Subscriber gebroadcastet.
 */
@Component
class DisconnectCleanupService(
    private val playerService: PlayerService,
    private val economyService: EconomyService,
    private val roomRegistry: RoomRegistry,
    private val messagingTemplate: SimpMessagingTemplate
) {
    companion object {
        const val GRACE_PERIOD_MS = 30_000L
        const val CLEANUP_INTERVAL_MS = 5_000L
    }

    /**
     * Wird alle [CLEANUP_INTERVAL_MS] Millisekunden ausgeführt.
     * Entfernt Spieler, deren Soft-Disconnect älter als [GRACE_PERIOD_MS] ist,
     * und broadcastet danach den aktualisierten GameState an alle Room-Subscriber.
     */
    @Scheduled(fixedDelay = CLEANUP_INTERVAL_MS)
    fun cleanupExpired() {
        val cutoff = System.currentTimeMillis() - GRACE_PERIOD_MS
        roomRegistry.getAllRooms().forEach { room ->
            val expired = synchronized(room.gameState.lock) {
                val toRemove = room.gameState.players.filter {
                    !it.connected && (it.disconnectedAt ?: Long.MAX_VALUE) < cutoff
                }
                toRemove.forEach { playerService.hardDelete(room.gameState, it) }
                toRemove
            }
            if (expired.isNotEmpty()) {
                economyService.recomputePlayerStats(room.gameState)
                messagingTemplate.convertAndSend(
                    "/topic/rooms/${room.roomId}/state",
                    room.gameState
                )
            }
        }
    }
}
