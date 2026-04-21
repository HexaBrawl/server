package at.aau.hexabrawl.websocketserver.websocket.broker

import at.aau.hexabrawl.websocketserver.messaging.dtos.StompMessage
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.stereotype.Controller

@Controller
class WebSocketBrokerController(
    private val gameService: GameService
) {

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
        return gameService.handleJoin(playerName)
    }

    // *****************
    // INIT (WebSocket)
    // *****************
    @MessageMapping("/init")
    @SendTo("/topic/game")
    fun init(): GameState {
        // TEST 4 Erwartung: aktueller Zustand kommt zurück, kein Reset!
        return gameService.getCurrentState()
    }

    // *****************
    // MOVE (WebSocket)
    // *****************
    @MessageMapping("/move")
    @SendTo("/topic/game")
    fun move(move: Move): GameState {
        return gameService.handleMove(move)
    }

    // **********************************************************
    // Hilfsmethoden für den TestController (REST-Schnittstelle)
    // **********************************************************
    fun handleJoin(name: String) = gameService.handleJoin(name)
    fun handleMove(move: Move) = gameService.handleMove(move)
    fun resetGameFromTest() = gameService.resetGame()

}
