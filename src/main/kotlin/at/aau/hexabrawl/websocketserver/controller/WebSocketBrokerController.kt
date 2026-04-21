package at.aau.hexabrawl.websocketserver.controller

import at.aau.hexabrawl.websocketserver.model.GameState
import at.aau.hexabrawl.websocketserver.model.Move
import at.aau.hexabrawl.websocketserver.model.StompMessage
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.stereotype.Controller

<<<<<<< HEAD:src/main/kotlin/at/aau/hexabrawl/websocketserver/websocket/broker/WebSocketBrokerController.kt
=======
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseBody
>>>>>>> origin/main:src/main/kotlin/at/aau/hexabrawl/websocketserver/controller/WebSocketBrokerController.kt

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
