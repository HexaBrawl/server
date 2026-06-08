package at.aau.hexabrawl.websocketserver.integration

import org.junit.jupiter.api.Test
import at.aau.hexabrawl.websocketserver.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)

class MultiRoomIntegrationTest {

    @Autowired
    private lateinit var roomRegistry: RoomRegistry

    @Autowired
    private lateinit var gameService: GameService

    //Test zurPrüfung ob Test-Infrastruktur funktioniert
    @Test
    fun `spring injects required beans`() {

        assertNotNull(roomRegistry)
        assertNotNull(gameService)
    }

    @Test
    fun `rooms run independently without state leaks`() {

        //Nur die Räume erzeugen:
        val roomA = roomRegistry.createRoom(
            GameMode.DUAL_VALLEY
        )

        val roomB = roomRegistry.createRoom(
            GameMode.DUAL_VALLEY
        )

        assertNotSame(
            roomA.gameState,
            roomB.gameState
        )

        //Spieler beitreten lassen:
        gameService.handleJoin(
            roomA.gameState,
            "Josef",
            "a1"
        )

        gameService.handleJoin(
            roomA.gameState,
            "Marie",
            "a2"
        )

        gameService.handleJoin(
            roomB.gameState,
            "Benedikt",
            "b1"
        )

        gameService.handleJoin(
            roomB.gameState,
            "Amalia",
            "b2"
        )

        //prüfen:
        assertEquals(
            GameStatus.IN_PROGRESS,
            roomA.gameState.status
        )

        assertEquals(
            GameStatus.IN_PROGRESS,
            roomB.gameState.status
        )

        //State-Leak-Test
        val unitsBefore = roomB.gameState.units.map {
            GameUnit(
                it.player,
                it.x,
                it.y,
                it.type
            )
        }

        val turnBefore = roomB.gameState.currentTurn

        // In DUAL_VALLEY muessen alle 3 Einheiten ziehen, bevor der Turn wechselt
        gameService.handleMove(roomA.gameState, Move("Josef", UnitType.ARCHER, 2, 2, 2, 3))
        gameService.handleMove(roomA.gameState, Move("Josef", UnitType.INFANTRY, 3, 2, 3, 3))
        gameService.handleMove(roomA.gameState, Move("Josef", UnitType.CAVALRY, 4, 2, 4, 3))

        //prüfen
        assertEquals(
            "Marie",
            roomA.gameState.currentTurn
        )

        //Der eigentliche Nachweis gegen State-Leaks
        assertEquals(
            unitsBefore,
            roomB.gameState.units
        )

        assertEquals(
            turnBefore,
            roomB.gameState.currentTurn
        )

        assertEquals(
            GameStatus.IN_PROGRESS,
            roomB.gameState.status
        )

    }

}