package at.aau.hexabrawl.websocketserver.websocket.broker

import at.aau.hexabrawl.websocketserver.messaging.dtos.StompMessage
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*

@Controller
class WebSocketBrokerController {

    private val gameState = GameState()

    companion object {
        const val MAX_PLAYERS = 2
    }

    // *******************************************
    // JOIN (REST) only for Testing with postman
    // *******************************************
    @PostMapping("/joinTest")
    @ResponseBody
    fun joinTest(@RequestBody name: String): GameState {
        return handleJoin(name)
    }

    fun handleJoin(playerName: String): GameState {

        println("JOIN: $playerName")

        // Prevent duplicates
        if (!gameState.players.contains(playerName)) {

            // Limit to 2 players
            if (gameState.players.size >= MAX_PLAYERS) {
                println("Game full!")
                return gameState
            }

            gameState.players.add(playerName)
        }

        // Start game when 2 players joined
        if (gameState.players.size == 2 && gameState.units.isEmpty()) {

            val p1 = gameState.players[0]
            val p2 = gameState.players[1]

            gameState.units.add(GameUnit(p1, 2, 2))
            gameState.units.add(GameUnit(p2, 5, 5))

            gameState.currentTurn = p1
            gameState.status = GameStatus.IN_PROGRESS
            println("GAME STARTED")
        }

        return gameState
    }
    // *********************
    // Send Hello to Server
    // *********************

    @MessageMapping("/hello")
    @SendTo("/topic/hello-response")
    fun handleHello(message: String): String {
        return "echo from broker: $message"
    }

    // *******************************************************************
    // Sends the same object back to all subscribers of /topic/rcv-object
    // *******************************************************************

    @MessageMapping("/object")
    @SendTo("/topic/rcv-object")
    fun handleObject(message: StompMessage): StompMessage {
        return message
    }

    // *****************
    // JOIN (WebSocket)
    // *****************
    @MessageMapping("/join")
    @SendTo("/topic/game")
    fun join(playerName: String): GameState {
        return handleJoin(playerName)
    }

    // *****************
    // INIT (WebSocket)
    // *****************
    @MessageMapping("/init")
    @SendTo("/topic/game")
    fun init(): GameState {
        println("INIT requested")
        return gameState
    }

    // *****************
    // MOVE (WebSocket)
    // *****************
    @MessageMapping("/move")
    @SendTo("/topic/game")
    fun handleMove(move: Move): GameState {

        println("Move from: ${move.player}")

        // Reject if game not started
        //if (gameState.currentTurn == null) {
        if (gameState.status == GameStatus.WAITING_FOR_PLAYERS) {
            println("REJECTED: Game not started")
            return gameState
        }

        // Reject wrong turn
        if (move.player != gameState.currentTurn) {
            println("REJECTED: Not your turn")
            return gameState
        }

        // Move unit
        gameState.units
            .firstOrNull { it.player == move.player }
            ?.apply {
                x = move.toX
                y = move.toY
            }

        // Switch turn
        if (gameState.players.size == 2) {
            val (p1, p2) = gameState.players

            gameState.currentTurn =
                if (gameState.currentTurn == p1) p2 else p1
        }

        return gameState
    }
}