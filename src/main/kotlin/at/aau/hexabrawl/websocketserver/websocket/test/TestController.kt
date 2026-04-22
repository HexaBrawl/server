package at.aau.hexabrawl.websocketserver.websocket.test

import at.aau.hexabrawl.websocketserver.model.GameService
import at.aau.hexabrawl.websocketserver.model.GameState
import at.aau.hexabrawl.websocketserver.model.Move
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/test")
class TestController(
    private val gameService: GameService,
    private val messagingTemplate: SimpMessagingTemplate
) {

    @PostMapping("/join")
    @ResponseBody
    fun join(@RequestBody name: String): GameState {
        val state = gameService.handleJoin(name)
        messagingTemplate.convertAndSend("/topic/game", state)
        return state
    }

    @PostMapping("/move")
    @ResponseBody
    fun move(@RequestBody move: Move): GameState {
        val state = gameService.handleMove(move)
        messagingTemplate.convertAndSend("/topic/game", state)
        return state
    }


    @PostMapping("/init")
    @ResponseBody
    fun init(): GameState {
        // ALLES auf Null (für einen komplett sauberen Testlauf)
        val state = gameService.initializeGame() // Deine bisherige resetGame() Logik
        messagingTemplate.convertAndSend("/topic/game", state)
        return state
    }

    @PostMapping("/reset")
    @ResponseBody
    fun reset(): GameState {
        // SPIELER BEHALTEN, aber Spielstand auf Anfang
        val state = gameService.resetToStartCondition()
        messagingTemplate.convertAndSend("/topic/game", state)
        return state
    }

}