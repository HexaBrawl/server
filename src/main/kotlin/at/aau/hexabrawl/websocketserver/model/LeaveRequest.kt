package at.aau.hexabrawl.websocketserver.model

/**
 * Request-DTO fuer /app/rooms/{roomId}/leave.
 *
 * Wird vom Client geschickt, wenn der User das Spiel bewusst verlaesst
 * (Button im UI, onDestroy mit isFinishing = true). Server fuehrt sofort
 * einen Hard-Delete durch — keine Grace Period.
 */
data class LeaveRequest(
    val playerName: String = ""
)
