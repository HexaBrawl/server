package at.aau.hexabrawl.websocketserver.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import at.aau.hexabrawl.websocketserver.TestServiceFactory
import at.aau.hexabrawl.websocketserver.service.BoardService

class GameModelTest {

    @Test
    fun `move covers all properties`() {
        val move = Move(
            player = "Alice",
            type = UnitType.INFANTRY,
            fromX = 1,
            fromY = 2,
            toX = 3,
            toY = 4
        )

        assertEquals("Alice", move.player)
        assertEquals(UnitType.INFANTRY, move.type)
        assertEquals(1, move.fromX)
        assertEquals(2, move.fromY)
        assertEquals(3, move.toX)
        assertEquals(4, move.toY)
    }

    @Test
    fun `move default constructor coverage`() {
        val move = Move()

        assertEquals("", move.player)
        assertEquals(UnitType.INFANTRY, move.type)
        assertEquals(0, move.fromX)
        assertEquals(0, move.fromY)
        assertEquals(0, move.toX)
        assertEquals(0, move.toY)
    }

    @Test
    fun `game unit covers all properties`() {
        val unit = GameUnit("Bob", 5, 6, UnitType.INFANTRY)

        assertEquals("Bob", unit.player)
        assertEquals(5, unit.x)
        assertEquals(6, unit.y)
    }

    @Test
    fun `game unit constructor`() {
        val unit = GameUnit("", 0, 0, UnitType.INFANTRY)

        assertEquals("", unit.player)
        assertEquals(0, unit.x)
        assertEquals(0, unit.y)
        assertEquals(UnitType.INFANTRY, unit.type)
    }

    @Test
    fun `game state mutation coverage`() {
        val state = GameState()
        state.players.add(Player("Alice"))
        state.units.add(GameUnit("Alice", 1, 1, UnitType.INFANTRY))
        state.currentTurn = "Alice"

        assertEquals(1, state.players.size)
        assertEquals(1, state.units.size)
        assertEquals("Alice", state.currentTurn)
    }

    @Test
    fun `test ErrorMessage and ErrorCode structure`() {
        val message = ErrorMessage(ErrorCode.INVALID_MOVE, "Test Fehler")

        assertEquals(ErrorCode.INVALID_MOVE, message.errorCode)
        assertEquals("Test Fehler", message.message)
    }

    @Test
    fun `ErrorMessage and ErrorCode coverage`() {
        val error = ErrorMessage(ErrorCode.GAME_FULL, "Spiel ist voll")
        assertEquals(ErrorCode.GAME_FULL, error.errorCode)
        assertEquals("Spiel ist voll", error.message)
    }

    @Test
    fun `StompMessage coverage`() {
        val msg = StompMessage("Server", "Test-Inhalt")
        assertEquals("Server", msg.from)
        assertEquals("Test-Inhalt", msg.text)
    }

    @Test
    fun `handleJoin only modifies provided state`() {
        val playerService = TestServiceFactory.createPlayerService()

        val state1 = GameState()
        val state2 = GameState()

        playerService.handleJoin(state1, "Josef", "session-1")

        assertEquals(1, state1.players.size)
        assertEquals(0, state2.players.size)
        assertEquals("Josef", state1.players[0].name)
    }

    @Test
    fun `handleMove only modifies provided state`() {
        val playerService = TestServiceFactory.createPlayerService()
        val turnService = TestServiceFactory.createTurnService()

        val state1 = GameState()
        val state2 = GameState()

        playerService.handleJoin(state1, "Josef", "s1")
        playerService.handleJoin(state1, "Marie", "s2")
        // Combat-Units manuell platzieren (Start-Einheiten werden nicht mehr automatisch gesetzt).
        state1.units.add(GameUnit("Josef", 2, 3, UnitType.INFANTRY))

        val move = Move("Josef", UnitType.INFANTRY, 2, 3, 2, 4)
        turnService.handleMove(state1, move)

        val movedUnit = state1.units.find { it.player == "Josef" && it.type == UnitType.INFANTRY }
        assertEquals(2, movedUnit?.x)
        assertEquals(4, movedUnit?.y)

        assertTrue(state2.units.isEmpty())
    }

    @Test
    fun `handleDisconnect only modifies provided state`() {
        val playerService = TestServiceFactory.createPlayerService()

        val state1 = GameState()
        val state2 = GameState()

        playerService.handleJoin(state1, "Josef", "s1")
        playerService.handleDisconnect(state1, "s1")

        // Soft-Disconnect: Josef bleibt im state1, aber connected = false
        assertEquals(1, state1.players.size)
        assertFalse(state1.players.first().connected)
        // state2 ist komplett unbeeinflusst
        assertTrue(state2.players.isEmpty())
        assertEquals(GameStatus.WAITING_FOR_PLAYERS, state2.status)
    }

    @Test
    fun `initializeGame only modifies provided state`() {
        val playerService = TestServiceFactory.createPlayerService()
        val boardService = BoardService()

        val state1 = GameState()
        val state2 = GameState()

        playerService.handleJoin(state1, "Josef", "s1")
        boardService.initializeGame(state1)

        assertTrue(state1.players.isEmpty())
        assertTrue(state2.players.isEmpty())
        assertTrue(state2.units.isEmpty())
    }

    @Test
    fun `resetToStartCondition only modifies provided state`() {
        val playerService = TestServiceFactory.createPlayerService()
        val boardService = BoardService()

        val state1 = GameState()
        val state2 = GameState()

        playerService.handleJoin(state1, "Josef", "s1")
        playerService.handleJoin(state1, "Marie", "s2")
        boardService.resetToStartCondition(state1)

        // Spieler bleiben erhalten
        assertEquals(2, state1.players.size)
        // Units existieren weiterhin
        assertFalse(state1.units.isEmpty())

        // Zweiter State bleibt unverändert
        assertTrue(state2.players.isEmpty())
        assertTrue(state2.units.isEmpty())
    }

    @Test
    fun `triad outpost does not start after second player joins`() {
        val playerService = TestServiceFactory.createPlayerService()
        val roomRegistry = RoomRegistry()

        val room = roomRegistry.createRoom(GameMode.TRIAD_OUTPOST)

        playerService.handleJoin(room.gameState, "P1", "session-1")
        playerService.handleJoin(room.gameState, "P2", "session-2")

        assertEquals(GameStatus.WAITING_FOR_PLAYERS, room.gameState.status)
    }

    @Test
    fun `triad outpost starts when third player joins`() {
        val playerService = TestServiceFactory.createPlayerService()
        val roomRegistry = RoomRegistry()

        val room = roomRegistry.createRoom(GameMode.TRIAD_OUTPOST)

        playerService.handleJoin(room.gameState, "P1", "session-1")
        playerService.handleJoin(room.gameState, "P2", "session-2")
        assertEquals(GameStatus.WAITING_FOR_PLAYERS, room.gameState.status)

        playerService.handleJoin(room.gameState, "P3", "session-3")
        assertEquals(GameStatus.IN_PROGRESS, room.gameState.status)
    }

    @Test
    fun `battlefield peaks does not start after third player joins`() {
        val playerService = TestServiceFactory.createPlayerService()
        val roomRegistry = RoomRegistry()

        val room = roomRegistry.createRoom(GameMode.BATTLEFIELD_PEAKS)

        playerService.handleJoin(room.gameState, "P1", "session-1")
        playerService.handleJoin(room.gameState, "P2", "session-2")
        playerService.handleJoin(room.gameState, "P3", "session-3")

        assertEquals(GameStatus.WAITING_FOR_PLAYERS, room.gameState.status)
    }

    @Test
    fun `battlefield peaks starts when fourth player joins`() {
        val playerService = TestServiceFactory.createPlayerService()
        val roomRegistry = RoomRegistry()

        val room = roomRegistry.createRoom(GameMode.BATTLEFIELD_PEAKS)

        playerService.handleJoin(room.gameState, "P1", "session-1")
        playerService.handleJoin(room.gameState, "P2", "session-2")
        playerService.handleJoin(room.gameState, "P3", "session-3")
        assertEquals(GameStatus.WAITING_FOR_PLAYERS, room.gameState.status)

        playerService.handleJoin(room.gameState, "P4", "session-4")
        assertEquals(GameStatus.IN_PROGRESS, room.gameState.status)
    }

    @Test
    fun `triad outpost turn stays after only one move`() {
        val playerService = TestServiceFactory.createPlayerService()
        val turnService = TestServiceFactory.createTurnService()
        val roomRegistry = RoomRegistry()

        val room = roomRegistry.createRoom(GameMode.TRIAD_OUTPOST)

        playerService.handleJoin(room.gameState, "P1", "session-1")
        playerService.handleJoin(room.gameState, "P2", "session-2")
        playerService.handleJoin(room.gameState, "P3", "session-3")

        assertEquals("P1", room.gameState.currentTurn)

        // P1 bewegt nur INFANTRY -- die anderen 2 Einheiten bleiben unbewegt.
        val state = turnService.handleMove(
            room.gameState,
            Move(player = "P1", type = UnitType.INFANTRY, fromX = 4, fromY = 9, toX = 4, toY = 10)
        ).state

        // Turn bleibt bei P1, weil ARCHER und CAVALRY noch nicht gezogen haben.
        assertEquals("P1", state.currentTurn)
    }

    @Test
    fun `battlefield peaks turn stays after only one move`() {
        val playerService = TestServiceFactory.createPlayerService()
        val turnService = TestServiceFactory.createTurnService()
        val roomRegistry = RoomRegistry()

        val room = roomRegistry.createRoom(GameMode.BATTLEFIELD_PEAKS)

        playerService.handleJoin(room.gameState, "P1", "session-1")
        playerService.handleJoin(room.gameState, "P2", "session-2")
        playerService.handleJoin(room.gameState, "P3", "session-3")
        playerService.handleJoin(room.gameState, "P4", "session-4")

        assertEquals("P1", room.gameState.currentTurn)

        val state = turnService.handleMove(
            room.gameState,
            Move(player = "P1", type = UnitType.INFANTRY, fromX = 6, fromY = 9, toX = 6, toY = 8)
        ).state

        // Turn bleibt bei P1, weil ARCHER und CAVALRY noch nicht gezogen haben.
        assertEquals("P1", state.currentTurn)
    }

    @Test
    fun `triad outpost switches turn after all three units moved`() {
        val playerService = TestServiceFactory.createPlayerService()
        val turnService = TestServiceFactory.createTurnService()
        val roomRegistry = RoomRegistry()

        val room = roomRegistry.createRoom(GameMode.TRIAD_OUTPOST)

        playerService.handleJoin(room.gameState, "P1", "session-1")
        playerService.handleJoin(room.gameState, "P2", "session-2")
        playerService.handleJoin(room.gameState, "P3", "session-3")
        // Combat-Units manuell platzieren (werden nicht mehr automatisch gesetzt).
        room.gameState.units.add(GameUnit("P1", 5, 8, UnitType.ARCHER))
        room.gameState.units.add(GameUnit("P1", 4, 9, UnitType.INFANTRY))
        room.gameState.units.add(GameUnit("P1", 6, 9, UnitType.CAVALRY))

        turnService.handleMove(room.gameState, Move("P1", UnitType.INFANTRY, 4, 9, 4, 10))
        assertEquals("P1", room.gameState.currentTurn)

        turnService.handleMove(room.gameState, Move("P1", UnitType.ARCHER, 5, 8, 5, 7))
        assertEquals("P1", room.gameState.currentTurn)

        turnService.handleMove(room.gameState, Move("P1", UnitType.CAVALRY, 6, 9, 6, 10))
        val state = turnService.endTurn(room.gameState, "P1")

        assertEquals("P2", state.currentTurn)
    }

    @Test
    fun `triad outpost endTurn forces switch with units remaining`() {
        val playerService = TestServiceFactory.createPlayerService()
        val turnService = TestServiceFactory.createTurnService()
        val roomRegistry = RoomRegistry()

        val room = roomRegistry.createRoom(GameMode.TRIAD_OUTPOST)

        playerService.handleJoin(room.gameState, "P1", "session-1")
        playerService.handleJoin(room.gameState, "P2", "session-2")
        playerService.handleJoin(room.gameState, "P3", "session-3")

        // P1 bewegt nur INFANTRY, ARCHER und CAVALRY bleiben.
        turnService.handleMove(room.gameState, Move("P1", UnitType.INFANTRY, 4, 9, 4, 10))
        assertEquals("P1", room.gameState.currentTurn)

        // Trotz unbewegter Einheiten beendet P1 manuell seinen Zug.
        val state = turnService.endTurn(room.gameState, "P1")

        assertEquals("P2", state.currentTurn)
    }

    @Test
    fun `battlefield peaks switches turn after all three units moved`() {
        val playerService = TestServiceFactory.createPlayerService()
        val turnService = TestServiceFactory.createTurnService()
        val roomRegistry = RoomRegistry()

        val room = roomRegistry.createRoom(GameMode.BATTLEFIELD_PEAKS)

        playerService.handleJoin(room.gameState, "P1", "session-1")
        playerService.handleJoin(room.gameState, "P2", "session-2")
        playerService.handleJoin(room.gameState, "P3", "session-3")
        playerService.handleJoin(room.gameState, "P4", "session-4")
        // Combat-Units manuell platzieren
        room.gameState.units.add(GameUnit("P1", 5, 9, UnitType.ARCHER))
        room.gameState.units.add(GameUnit("P1", 6, 9, UnitType.INFANTRY))
        room.gameState.units.add(GameUnit("P1", 7, 9, UnitType.CAVALRY))

        turnService.handleMove(room.gameState, Move("P1", UnitType.INFANTRY, 6, 9, 6, 8))
        assertEquals("P1", room.gameState.currentTurn)

        turnService.handleMove(room.gameState, Move("P1", UnitType.ARCHER, 5, 9, 5, 10))
        assertEquals("P1", room.gameState.currentTurn)

        turnService.handleMove(room.gameState, Move("P1", UnitType.CAVALRY, 7, 9, 7, 10))
        val state = turnService.endTurn(room.gameState, "P1")

        assertEquals("P2", state.currentTurn)
    }
}
