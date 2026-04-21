package at.aau.hexabrawl.websocketserver.websocket.test

import at.aau.hexabrawl.websocketserver.websocket.broker.*
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/test")
class TestController(
    private val broker: WebSocketBrokerController,
    // Template ermöglicht das manuelle Senden an WebSocket-Topics
    private val messagingTemplate: SimpMessagingTemplate
) {

    /*@PostMapping("/join")
    fun join(@RequestBody name: String): GameState {
        return broker.handleJoin(name)
    }*/
    @PostMapping("/join")
    fun join(@RequestBody name: String): GameState {
        val state = broker.handleJoin(name)
        // Informiert alle WebSocket-Clients (Handy) über den neuen Player
        messagingTemplate.convertAndSend("/topic/game", state)
        return state
    }

    /*@PostMapping("/move")
    fun move(@RequestBody move: Move): GameState {
        return broker.handleMove(move)
    }*/
    @PostMapping("/move")
    fun move(@RequestBody move: Move): GameState {
        val state = broker.handleMove(move)
        // Das triggert das 'GAME UPDATE' Log in deinem MyStomp.kt
        messagingTemplate.convertAndSend("/topic/game", state)
        return state
    }

    /*@GetMapping("/init")
    fun init(): GameState {
        return broker.init()
    }*/
    @GetMapping("/init")
    fun init(): GameState {
        val state = broker.init()
        messagingTemplate.convertAndSend("/topic/game", state)
        return state
    }

    @PostMapping("/reset")
    fun reset(): GameState {
        val state = broker.resetGameFromTest() // Wir erstellen gleich die Brücke im Broker
        // Optional: Alle verbundenen Handys informieren, dass das Spiel gelöscht wurde
        messagingTemplate.convertAndSend("/topic/game", state)
        return state
    }


}