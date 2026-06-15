package at.aau.hexabrawl.websocketserver.websocket.test

import at.aau.hexabrawl.websocketserver.service.GameService
import at.aau.hexabrawl.websocketserver.model.GameState
import at.aau.hexabrawl.websocketserver.model.Move
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.web.bind.annotation.*
import java.util.UUID

/**
 * Test-/Demo-REST-Endpoints (`/test/...`), die direkt auf den globalen
 * Single-Game-State von [GameService.gameState] arbeiten. Nicht von der
 * Production-App genutzt — gedacht fuer manuelle Curl-Smoke-Tests und
 * frueheres Frontend-Demo. Kandidat fuer Cleanup wenn nicht mehr
 * gebraucht.
 */
@RestController
@RequestMapping("/test")
class TestController(
    private val gameService: GameService,
    private val messagingTemplate: SimpMessagingTemplate
)
    {
        companion object {
            const val GAME_TOPIC = "/topic/game"
    }

    @PostMapping("/join")
    @ResponseBody
    fun join(@RequestBody name: String): GameState {

        val fakeSessionId = UUID.randomUUID().toString()

        val state =
            gameService.handleJoin(name, fakeSessionId)

        messagingTemplate.convertAndSend(GAME_TOPIC, state)

        return state
    }

    @PostMapping("/move")
    @ResponseBody
    fun move(@RequestBody move: Move): GameState {
        val state = gameService.handleMove(move)
        messagingTemplate.convertAndSend(GAME_TOPIC, state)
        return state
    }


    @PostMapping("/init")
    @ResponseBody
    fun init(): GameState {
        // ALLES auf Null (für einen komplett sauberen Testlauf)
        val state = gameService.initializeGame() // bisherige resetGame() Logik
        messagingTemplate.convertAndSend(GAME_TOPIC, state)
        return state
    }

    @PostMapping("/reset")
    @ResponseBody
    fun reset(): GameState {
        // SPIELER BEHALTEN, aber Spielstand auf Anfang
        val state = gameService.resetToStartCondition()
        messagingTemplate.convertAndSend(GAME_TOPIC, state)
        return state
    }

}