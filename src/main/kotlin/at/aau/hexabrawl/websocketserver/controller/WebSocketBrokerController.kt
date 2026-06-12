package at.aau.hexabrawl.websocketserver.controller

import at.aau.hexabrawl.websocketserver.model.BuyUnitRequest
import at.aau.hexabrawl.websocketserver.model.ClaimGiftRequest
import at.aau.hexabrawl.websocketserver.model.EndTurnRequest
import at.aau.hexabrawl.websocketserver.model.ErrorCode
import at.aau.hexabrawl.websocketserver.model.ErrorMessage
import at.aau.hexabrawl.websocketserver.model.GameService
import at.aau.hexabrawl.websocketserver.model.GameState
import at.aau.hexabrawl.websocketserver.model.GameStatus
import at.aau.hexabrawl.websocketserver.model.JoinRequest
import at.aau.hexabrawl.websocketserver.model.Move
import at.aau.hexabrawl.websocketserver.model.PlayerColor
import at.aau.hexabrawl.websocketserver.model.RoomRegistry
import at.aau.hexabrawl.websocketserver.model.StealResponseRequest
import at.aau.hexabrawl.websocketserver.model.StompMessage
import at.aau.hexabrawl.websocketserver.model.UnitType
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Controller

@Controller
class WebSocketBrokerController(
    private val gameService: GameService,
    private val roomRegistry: RoomRegistry,
    private val messagingTemplate: SimpMessagingTemplate
) {
    companion object {
        private const val ROOM_NOT_FOUND_MESSAGE = "Raum nicht gefunden."
    }

    @MessageMapping("/hello")
    @SendTo("/topic/hello-response")
    fun handleHello(message: String): String {
        return "echo from broker: $message"
    }

    @MessageMapping("/object")
    @SendTo("/topic/rcv-object")
    fun handleObject(message: StompMessage): StompMessage {
        return message
    }

    private fun sendRoomState(roomId: String, state: GameState) {
        gameService.recomputePlayerStats(state)
        messagingTemplate.convertAndSend("/topic/rooms/${roomId}/state", state)

    }

    @MessageMapping("/rooms/{roomId}/join")
    fun joinRoom(
        @DestinationVariable roomId: String,
        request: JoinRequest,
        headerAccessor: SimpMessageHeaderAccessor
    ): GameState? {
        val sessionId = headerAccessor.sessionId ?: ""

        val room = roomRegistry.findById(roomId)
        if (room == null) {
            sendError(sessionId, ErrorCode.ROOM_NOT_FOUND, ROOM_NOT_FOUND_MESSAGE)
            return null
        }

        val currentState = gameService.getCurrentState(room.gameState)

        if (currentState.players.size >= room.mode.maxPlayers &&
            !currentState.players.any { it.name == request.name }
        ) {
            sendError(sessionId, ErrorCode.GAME_FULL, "Beitritt verweigert: Spiel ist voll.")
            return null
        }

        val requestedColor = request.color
        if (requestedColor != null &&
            !currentState.players.any { it.name == request.name } &&
            gameService.isColorTaken(room.gameState, requestedColor)
        ) {
            sendError(
                sessionId,
                ErrorCode.COLOR_ALREADY_TAKEN,
                "Beitritt verweigert: Farbe '$requestedColor' ist bereits vergeben."
            )
            return null
        }

        val state = gameService.handleJoin(
            room.gameState,
            request.name,
            sessionId,
            request.color
        )

        sendRoomState(roomId, state)
        return state
    }

    @MessageMapping("/rooms/{roomId}/init")
    fun initRoom(
        @DestinationVariable roomId: String,
        headerAccessor: SimpMessageHeaderAccessor
    ): GameState? {
        val sessionId = headerAccessor.sessionId ?: ""

        val room = roomRegistry.findById(roomId)
        if (room == null) {
            sendError(sessionId, ErrorCode.ROOM_NOT_FOUND, ROOM_NOT_FOUND_MESSAGE)
            return null
        }

        val state = gameService.getCurrentState(room.gameState)
        sendRoomState(roomId, state)
        return state
    }

    @MessageMapping("/rooms/{roomId}/move")
    fun moveRoom(
        @DestinationVariable roomId: String,
        move: Move,
        headerAccessor: SimpMessageHeaderAccessor
    ): GameState? {
        val sessionId = headerAccessor.sessionId ?: ""

        val room = roomRegistry.findById(roomId)
        if (room == null) {
            sendError(sessionId, ErrorCode.ROOM_NOT_FOUND, ROOM_NOT_FOUND_MESSAGE)
            return null
        }

        val stateBefore = gameService.getCurrentState(room.gameState)

        if (stateBefore.status != GameStatus.IN_PROGRESS) {
            sendError(sessionId, ErrorCode.GAME_NOT_STARTED, "Zug abgelehnt: Spiel läuft nicht.")
            return null
        }

        if (!assertNoPendingGift(stateBefore, sessionId)) return null

        if (move.player != stateBefore.currentTurn) {
            sendError(sessionId, ErrorCode.NOT_YOUR_TURN, "Es ist nicht dein Zug!")
            return null
        }

        // Snapshot vor dem Move - wenn sich nach handleMove nichts geaendert hat,
        // wurde der Move abgelehnt. Currentturn-Vergleich reicht hier nicht mehr,
        // weil der Turn mit dem Rundensystem nicht nach jedem Move switcht.
        val unitsBefore = stateBefore.units.map {
            "${it.player}-${it.type}-${it.x},${it.y}"
        }.toSet()

        val stateAfter = gameService.handleMove(room.gameState, move)

        val unitsAfter = stateAfter.units.map {
            "${it.player}-${it.type}-${it.x},${it.y}"
        }.toSet()

        if (unitsBefore == unitsAfter) {
            sendError(sessionId, ErrorCode.INVALID_MOVE, "Dieser Zug ist laut Regeln ungültig.")
            return null
        }

        sendRoomState(roomId, stateAfter)
        return stateAfter
    }

    @MessageMapping("/rooms/{roomId}/end-turn")
    fun endTurnRoom(
        @DestinationVariable roomId: String,
        request: EndTurnRequest,
        headerAccessor: SimpMessageHeaderAccessor
    ): GameState? {
        val sessionId = headerAccessor.sessionId ?: ""

        val room = roomRegistry.findById(roomId)
        if (room == null) {
            sendError(sessionId, ErrorCode.ROOM_NOT_FOUND, ROOM_NOT_FOUND_MESSAGE)
            return null
        }

        val stateBefore = gameService.getCurrentState(room.gameState)

        if (stateBefore.status != GameStatus.IN_PROGRESS) {
            sendError(sessionId, ErrorCode.GAME_NOT_STARTED, "Runde beenden abgelehnt: Spiel laeuft nicht.")
            return null
        }

        if (!assertNoPendingGift(stateBefore, sessionId)) return null

        if (stateBefore.currentTurn != request.playerName) {
            sendError(sessionId, ErrorCode.NOT_YOUR_TURN, "Du kannst nicht die Runde eines anderen Spielers beenden!")
            return null
        }

        val state = gameService.endTurn(room.gameState, request.playerName)
        sendRoomState(roomId, state)
        return state
    }

    @MessageMapping("/rooms/{roomId}/buy-farm")
    fun buyFarmRoom(
        @DestinationVariable roomId: String,
        headerAccessor: SimpMessageHeaderAccessor
    ): GameState? {
        val sessionId = headerAccessor.sessionId ?: ""

        val room = roomRegistry.findById(roomId)
        if (room == null) {
            sendError(sessionId, ErrorCode.ROOM_NOT_FOUND, "Raum nicht gefunden.")
            return null
        }

        val state = room.gameState

        if (state.status != GameStatus.IN_PROGRESS) {
            sendError(sessionId, ErrorCode.GAME_NOT_STARTED, "Spiel ist nicht gestartet.")
            return null
        }

        if (!assertNoPendingGift(state, sessionId)) return null

        val player = state.players.find { it.sessionId == sessionId }
        if (player == null) return null

        if (state.currentTurn != player.name) {
            sendError(sessionId, ErrorCode.NOT_YOUR_TURN, "Du bist nicht am Zug.")
            return null
        }

        val cost = GameService.FARM_BASE_COST + (player.farms * GameService.FARM_COST_INCREMENT)

        if (player.gold < cost) {
            sendError(sessionId, ErrorCode.INSUFFICIENT_GOLD, "Nicht genug Gold.")
            return null
        }

        // Werte direkt hier updaten
        synchronized(state.lock) {
            player.gold -= cost
            player.farms += 1
            player.income = player.farms * GameService.FARM_INCOME_PER_ROUND
        }

        sendRoomState(roomId, state)
        return state
    }

    /**
     * Kauft eine Einheit fuer den Spieler im angegebenen Room und
     * platziert sie an (x, y) (#132).
     *
     * Validierungs-Reihenfolge (billig vor teuer):
     *  1. Room existiert? sonst ROOM_NOT_FOUND
     *  2. Status IN_PROGRESS? sonst GAME_NOT_STARTED
     *  3. Spieler am Zug? sonst NOT_YOUR_TURN
     *  4. Type INFANTRY/ARCHER/CAVALRY? sonst INVALID_PLACEMENT
     *  5. Zielfeld gehoert dem Spieler? sonst INVALID_PLACEMENT
     *  6. Zielfeld nicht von eigener Einheit/Basis besetzt? sonst
     *     INVALID_PLACEMENT
     *  7. Genug Gold? sonst INSUFFICIENT_GOLD
     *
     * Bei Erfolg: delegiert an gameService.buyUnit und broadcastet
     * den neuen GameState auf /topic/rooms/{roomId}/state.
     */
    @MessageMapping("/rooms/{roomId}/buy-unit")
    fun buyUnitRoom(
        @DestinationVariable roomId: String,
        request: BuyUnitRequest,
        headerAccessor: SimpMessageHeaderAccessor
    ): GameState? {
        val sessionId = headerAccessor.sessionId ?: ""

        val room = roomRegistry.findById(roomId)
        if (room == null) {
            sendError(sessionId, ErrorCode.ROOM_NOT_FOUND, ROOM_NOT_FOUND_MESSAGE)
            return null
        }

        val state = room.gameState

        if (state.status != GameStatus.IN_PROGRESS) {
            sendError(sessionId, ErrorCode.GAME_NOT_STARTED, "Kauf abgelehnt: Spiel laeuft nicht.")
            return null
        }

        if (!assertNoPendingGift(state, sessionId)) return null

        if (request.playerName != state.currentTurn) {
            sendError(sessionId, ErrorCode.NOT_YOUR_TURN, "Es ist nicht dein Zug.")
            return null
        }

        if (request.type == UnitType.BASE || request.type == UnitType.SKELETON) {
            sendError(
                sessionId,
                ErrorCode.INVALID_PLACEMENT,
                "Dieser Einheitstyp kann nicht gekauft werden."
            )
            return null
        }

        // Zielfeld muss dem Spieler gehoeren (#104 Field-Ownership)
        val field = state.fields.firstOrNull { it.x == request.x && it.y == request.y }
        if (field?.owner != request.playerName) {
            sendError(
                sessionId,
                ErrorCode.INVALID_PLACEMENT,
                "Einheit kann nur auf eigenen Feldern platziert werden."
            )
            return null
        }

        // Zielfeld darf kein Skelett-Feld (abgeschnitten) sein
        if (field.isSkeleton) {
            sendError(
                sessionId,
                ErrorCode.INVALID_PLACEMENT,
                "Einheit kann nicht auf abgeschnittenen Feldern platziert werden."
            )
            return null
        }

        // Zielfeld darf nicht von eigener Einheit (inkl. BASE) besetzt sein.
        // Skelette zaehlen nicht als "besetzt" — werden vom Service entfernt.
        val occupiedByOwn = state.units.any {
            it.x == request.x && it.y == request.y &&
                    it.player == request.playerName &&
                    it.type != UnitType.SKELETON
        }
        if (occupiedByOwn) {
            sendError(
                sessionId,
                ErrorCode.INVALID_PLACEMENT,
                "Feld ist bereits besetzt."
            )
            return null
        }

        val player = state.players.find { it.name == request.playerName }
        if (player == null) {
            sendError(
                sessionId,
                ErrorCode.INVALID_PLACEMENT,
                "Spieler nicht gefunden."
            )
            return null
        }

        if (player.gold < GameService.UNIT_PRICE) {
            sendError(sessionId, ErrorCode.INSUFFICIENT_GOLD, "Nicht genug Gold.")
            return null
        }

        val updated = gameService.buyUnit(
            state,
            request.playerName,
            request.type,
            request.x,
            request.y
        )
        sendRoomState(roomId, updated)
        return updated
    }


    /**
     * Oeffnet ein Schummel-Geschenk fuer den Spieler.
     *
     * Validierungs-Reihenfolge (billig vor teuer):
     *  1. Room existiert? sonst ROOM_NOT_FOUND
     *  2. Status IN_PROGRESS? sonst GAME_NOT_STARTED
     *  3. delta in -10..10? sonst INVALID_CHEAT_DELTA
     *  4. Kein pendingGift aktiv? sonst CHEAT_ALREADY_PENDING
     *  5. Spieler existiert? sonst no-op
     *  6. hasUsedGift false? sonst CHEAT_ALREADY_USED
     *
     * Bei Erfolg: delegiert an gameService.claimCheatGift und broadcastet.
     */
    @MessageMapping("/rooms/{roomId}/cheat/claim-gift")
    fun claimCheatGiftRoom(
        @DestinationVariable roomId: String,
        request: ClaimGiftRequest,
        headerAccessor: SimpMessageHeaderAccessor
    ): GameState? {
        val sessionId = headerAccessor.sessionId ?: ""

        val room = roomRegistry.findById(roomId)
        if (room == null) {
            sendError(sessionId, ErrorCode.ROOM_NOT_FOUND, ROOM_NOT_FOUND_MESSAGE)
            return null
        }

        val state = room.gameState

        if (state.status != GameStatus.IN_PROGRESS) {
            sendError(sessionId, ErrorCode.GAME_NOT_STARTED, "Spiel ist nicht gestartet.")
            return null
        }

        if (request.delta !in -10..10) {
            sendError(sessionId, ErrorCode.INVALID_CHEAT_DELTA, "Delta muss zwischen -10 und +10 liegen.")
            return null
        }

        if (state.pendingGift != null) {
            sendError(sessionId, ErrorCode.CHEAT_ALREADY_PENDING, "Es laeuft bereits ein Geschenk.")
            return null
        }

        val player = state.players.find { it.name == request.playerName }
        if (player == null) return null

        if (player.hasUsedGift) {
            sendError(sessionId, ErrorCode.CHEAT_ALREADY_USED, "Du hast dein Geschenk schon benutzt.")
            return null
        }

        val updated = gameService.claimCheatGift(state, request.playerName, request.delta)
        sendRoomState(roomId, updated)
        return updated
    }


    /**
     * Antwort auf das Schummel-Geschenk: "Ja klauen" oder "Nein".
     *
     * Validierungs-Reihenfolge (billig vor teuer):
     *  1. Room existiert? sonst ROOM_NOT_FOUND
     *  2. pendingGift aktiv? sonst NO_PENDING_GIFT
     *  3. playerName != owner? sonst OWNER_CANNOT_STEAL
     *  4. Spieler existiert? sonst no-op
     *
     * Bei Erfolg: delegiert an gameService.respondCheatSteal und broadcastet.
     */
    @MessageMapping("/rooms/{roomId}/cheat/respond-steal")
    fun respondCheatStealRoom(
        @DestinationVariable roomId: String,
        request: StealResponseRequest,
        headerAccessor: SimpMessageHeaderAccessor
    ): GameState? {
        val sessionId = headerAccessor.sessionId ?: ""

        val room = roomRegistry.findById(roomId)
        if (room == null) {
            sendError(sessionId, ErrorCode.ROOM_NOT_FOUND, ROOM_NOT_FOUND_MESSAGE)
            return null
        }

        val state = room.gameState
        val gift = state.pendingGift
        if (gift == null) {
            sendError(sessionId, ErrorCode.NO_PENDING_GIFT, "Es laeuft kein Geschenk.")
            return null
        }

        if (request.playerName == gift.ownerName) {
            sendError(sessionId, ErrorCode.OWNER_CANNOT_STEAL, "Du kannst dein eigenes Geschenk nicht stehlen.")
            return null
        }

        val player = state.players.find { it.name == request.playerName }
        if (player == null) return null

        val updated = gameService.respondCheatSteal(state, request.playerName, request.accept)
        sendRoomState(roomId, updated)
        return updated
    }


    // Bridges fuer Tests, die noch ueber den globalen GameState gehen.
    fun handleJoin(name: String, sessionId: String, color: PlayerColor? = null) =
        gameService.handleJoin(name, sessionId, color)
    fun handleMove(move: Move) = gameService.handleMove(move)
    fun endTurn(playerName: String, headerAccessor: SimpMessageHeaderAccessor): GameState? {
        val sessionId = headerAccessor.sessionId ?: ""
        val stateBefore = gameService.getCurrentState()
        if (stateBefore.status != GameStatus.IN_PROGRESS) {
            sendError(sessionId, ErrorCode.GAME_NOT_STARTED, "Runde beenden abgelehnt: Spiel laeuft nicht.")
            return null
        }
        if (stateBefore.currentTurn != playerName) {
            sendError(sessionId, ErrorCode.NOT_YOUR_TURN, "Du kannst nicht die Runde eines anderen Spielers beenden!")
            return null
        }
        return gameService.endTurn(playerName)
    }

    private fun sendError(user: String, code: ErrorCode, msg: String) {
        val errorResponse = ErrorMessage(code, msg)
        messagingTemplate.convertAndSendToUser(user, "/queue/errors", errorResponse)
    }

    /**
     * Prueft, ob aktuell ein Cheat-Geschenk auf Antwort wartet. Falls ja,
     * wird GIFT_PENDING an den anfragenden Spieler zurueckgemeldet und
     * der aufrufende Endpoint soll abbrechen.
     *
     * @return true wenn kein pendingGift aktiv (Endpoint darf weitermachen),
     *         false wenn pendingGift aktiv (Error bereits gesendet).
     */
    private fun assertNoPendingGift(state: GameState, sessionId: String): Boolean {
        if (state.pendingGift != null) {
            sendError(sessionId, ErrorCode.GIFT_PENDING, "Warte auf Cheat-Entscheidung der Gegner.")
            return false
        }
        return true
    }
}
