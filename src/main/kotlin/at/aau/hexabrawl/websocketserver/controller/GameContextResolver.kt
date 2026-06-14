package at.aau.hexabrawl.websocketserver.controller

import at.aau.hexabrawl.websocketserver.model.ErrorCode
import at.aau.hexabrawl.websocketserver.model.ErrorMessage
import at.aau.hexabrawl.websocketserver.model.GameState
import at.aau.hexabrawl.websocketserver.model.GameStatus
import at.aau.hexabrawl.websocketserver.model.Room
import at.aau.hexabrawl.websocketserver.model.RoomRegistry
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component

/**
 * Bündelt die wiederkehrenden Vorab-Checks aller Room-Endpoints und das
 * Versenden der zugehörigen Error-Frames.
 *
 * Vorher standen Room-Lookup, GameStatus-Check, PendingGift-Check und
 * Current-Turn-Check sechsmal duplikat im WebSocketBrokerController.
 * Der Resolver zentralisiert sie und sendet bei Fehlschlag direkt die
 * passende ErrorMessage an /user/queue/errors zurück.
 */
@Component
class GameContextResolver(
    private val roomRegistry: RoomRegistry,
    private val messagingTemplate: SimpMessagingTemplate
) {

    /**
     * Zusammengefasstes Ergebnis eines erfolgreichen Resolve-Aufrufs.
     */
    data class Context(
        val room: Room,
        val state: GameState
    )

    /**
     * Sucht den Raum anhand der [roomId]. Existiert er nicht, wird ein
     * ROOM_NOT_FOUND-Error an den Session-User gesendet und null
     * zurückgegeben.
     *
     * Geeignet für Endpoints ohne weitere Vorbedingungen
     * (z.B. /init, /reconnect, /leave, /join).
     */
    fun resolveRoom(sessionId: String, roomId: String): Context? {
        val room = roomRegistry.findById(roomId)
        if (room == null) {
            sendError(sessionId, ErrorCode.ROOM_NOT_FOUND, ROOM_NOT_FOUND_MESSAGE)
            return null
        }
        return Context(room, room.gameState)
    }

    /**
     * Sucht den Raum und prüft zusätzlich, dass das Spiel im Status
     * IN_PROGRESS ist. Optional kann der erwartete Spielername am Zug
     * verlangt werden und/oder dass kein Cheat-Geschenk gerade auf
     * Antwort wartet.
     *
     * Verwendung in Endpoints, die nur während eines laufenden Spielzugs
     * gültig sind (/move, /end-turn, /buy-farm, /buy-unit,
     * /cheat/claim-gift).
     *
     * Bei jedem Fehlschlag wird der passende Error-Code an den
     * Session-User geschickt und null zurückgegeben.
     */
    fun resolveActiveGame(
        sessionId: String,
        roomId: String,
        gameNotStartedMessage: String = "Spiel ist nicht gestartet.",
        notYourTurnMessage: String = "Du bist nicht am Zug.",
        expectedCurrentTurn: String? = null,
        requireNoPendingGift: Boolean = false
    ): Context? {
        val ctx = resolveRoom(sessionId, roomId) ?: return null
        val state = ctx.state

        if (state.status != GameStatus.IN_PROGRESS) {
            sendError(sessionId, ErrorCode.GAME_NOT_STARTED, gameNotStartedMessage)
            return null
        }

        if (requireNoPendingGift && state.pendingGift != null) {
            sendError(sessionId, ErrorCode.GIFT_PENDING, PENDING_GIFT_MESSAGE)
            return null
        }

        if (expectedCurrentTurn != null && state.currentTurn != expectedCurrentTurn) {
            sendError(sessionId, ErrorCode.NOT_YOUR_TURN, notYourTurnMessage)
            return null
        }

        return ctx
    }

    /**
     * Versendet eine ErrorMessage an /user/queue/errors des angegebenen
     * Session-Users. Wird auch direkt vom Controller für Endpoint-spezifische
     * Fehler benutzt, deren Validierungen über die generischen
     * Resolver-Checks hinausgehen.
     */
    fun sendError(user: String, code: ErrorCode, msg: String) {
        messagingTemplate.convertAndSendToUser(user, "/queue/errors", ErrorMessage(code, msg))
    }

    companion object {
        const val ROOM_NOT_FOUND_MESSAGE = "Raum nicht gefunden."
        const val PENDING_GIFT_MESSAGE = "Warte auf Cheat-Entscheidung der Gegner."
    }
}
