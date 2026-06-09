package at.aau.hexabrawl.websocketserver.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

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

        val gameService = GameService(CombatService())

        val state1 = GameState()
        val state2 = GameState()

        gameService.handleJoin(
            state1,
            "Josef",
            "session-1"
        )

        assertEquals(1, state1.players.size)
        assertEquals(0, state2.players.size)

        assertEquals(
            "Josef",
            state1.players[0].name
        )
    }

    @Test
    fun `legacy handleJoin bridge still uses gameState`() {

        val gameService = GameService(CombatService())

        gameService.handleJoin(
            "Josef",
            "session-1"
        )

        assertEquals(
            1,
            gameService.gameState.players.size
        )

        assertEquals(
            "Josef",
            gameService.gameState.players[0].name
        )
    }

    @Test
    fun `handleMove only modifies provided state`() {
        val gameService = GameService(CombatService())

        val state1 = GameState()
        val state2 = GameState()

        gameService.handleJoin(state1, "Josef", "s1")
        gameService.handleJoin(state1, "Marie", "s2")

        val move = Move(
            "Josef",
            UnitType.INFANTRY,
            2,
            3,
            2,
            4
        )

        gameService.handleMove(state1, move)

        val movedUnit =
            state1.units.find {
                it.player == "Josef" &&
                        it.type == UnitType.INFANTRY
            }

        assertEquals(2, movedUnit?.x)
        assertEquals(4, movedUnit?.y)

        assertTrue(state2.units.isEmpty())
    }

    @Test
    fun `legacy handleMove bridge still uses gameState`() {
        val gameService = GameService(CombatService())

        gameService.handleJoin("Josef", "s1")
        gameService.handleJoin("Marie", "s2")

        val move = Move(
            "Josef",
            UnitType.INFANTRY,
            2,
            3,
            2,
            4
        )

        gameService.handleMove(move)

        val movedUnit =
            gameService.gameState.units.find {
                it.player == "Josef" &&
                        it.type == UnitType.INFANTRY
            }

        assertEquals(2, movedUnit?.x)
        assertEquals(4, movedUnit?.y)
    }

    @Test
    fun `handleDisconnect only modifies provided state`() {

        val gameService = GameService(CombatService())

        val state1 = GameState()
        val state2 = GameState()

        gameService.handleJoin(
            state1,
            "Josef",
            "s1"
        )

        gameService.handleDisconnect(
            state1,
            "s1"
        )

        assertTrue(state1.players.isEmpty())
        assertTrue(state2.players.isEmpty())

        assertEquals(
            GameStatus.WAITING_FOR_PLAYERS,
            state2.status
        )
    }

    @Test
    fun `legacy handleDisconnect bridge still uses gameState`() {

        val gameService = GameService(CombatService())

        gameService.handleJoin(
            "Josef",
            "s1"
        )

        gameService.handleDisconnect("s1")

        assertTrue(
            gameService.gameState.players.isEmpty()
        )
    }

    @Test
    fun `initializeGame only modifies provided state`() {

        val gameService = GameService(CombatService())

        val state1 = GameState()
        val state2 = GameState()

        gameService.handleJoin(state1, "Josef", "s1")

        gameService.initializeGame(state1)

        assertTrue(state1.players.isEmpty())

        assertTrue(state2.players.isEmpty())

        assertTrue(state2.units.isEmpty())
    }

    @Test
    fun `resetToStartCondition only modifies provided state`() {

        val gameService = GameService(CombatService())

        val state1 = GameState()
        val state2 = GameState()

        gameService.handleJoin(state1, "Josef", "s1")
        gameService.handleJoin(state1, "Marie", "s2")

        gameService.resetToStartCondition(state1)

        // Spieler bleiben erhalten
        assertEquals(2, state1.players.size)

        // Units existieren weiterhin
        assertFalse(state1.units.isEmpty())

        // Zweiter State bleibt unverändert
        assertTrue(state2.players.isEmpty())
        assertTrue(state2.units.isEmpty())
    }

    @Test
    fun `getCurrentState returns provided state`() {

        val gameService = GameService(CombatService())

        val state1 = GameState()

        val result = gameService.getCurrentState(state1)

        assertSame(state1, result)
    }


    @Test
    fun `triad outpost does not start after second player joins`() {
        val gameService = GameService(CombatService())
        val roomRegistry = RoomRegistry()

        val room = roomRegistry.createRoom(
            GameMode.TRIAD_OUTPOST
        )

        gameService.handleJoin(
            room.gameState,
            "P1",
            "session-1"
        )

        gameService.handleJoin(
            room.gameState,
            "P2",
            "session-2"
        )
        println(room.mode)
        println(room.gameState.gameMode)

        assertEquals(
            GameStatus.WAITING_FOR_PLAYERS,
            room.gameState.status
        )
    }

    @Test
    fun `triad outpost starts when third player joins`() {

        val gameService = GameService(CombatService())
        val roomRegistry = RoomRegistry()

        val room = roomRegistry.createRoom(
            GameMode.TRIAD_OUTPOST
        )

        gameService.handleJoin(
            room.gameState,
            "P1",
            "session-1"
        )

        gameService.handleJoin(
            room.gameState,
            "P2",
            "session-2"
        )

        assertEquals(
            GameStatus.WAITING_FOR_PLAYERS,
            room.gameState.status
        )

        gameService.handleJoin(
            room.gameState,
            "P3",
            "session-3"
        )

        assertEquals(
            GameStatus.IN_PROGRESS,
            room.gameState.status
        )
    }


    @Test
    fun `battlefield peaks does not start after third player joins`() {

        val gameService = GameService(CombatService())
        val roomRegistry = RoomRegistry()

        val room = roomRegistry.createRoom(
            GameMode.BATTLEFIELD_PEAKS
        )

        gameService.handleJoin(
            room.gameState,
            "P1",
            "session-1"
        )

        gameService.handleJoin(
            room.gameState,
            "P2",
            "session-2"
        )

        gameService.handleJoin(
            room.gameState,
            "P3",
            "session-3"
        )

        assertEquals(
            GameStatus.WAITING_FOR_PLAYERS,
            room.gameState.status
        )
    }

    @Test
    fun `battlefield peaks starts when fourth player joins`() {

        val gameService = GameService(CombatService())
        val roomRegistry = RoomRegistry()

        val room = roomRegistry.createRoom(
            GameMode.BATTLEFIELD_PEAKS
        )

        gameService.handleJoin(
            room.gameState,
            "P1",
            "session-1"
        )

        gameService.handleJoin(
            room.gameState,
            "P2",
            "session-2"
        )

        gameService.handleJoin(
            room.gameState,
            "P3",
            "session-3"
        )

        assertEquals(
            GameStatus.WAITING_FOR_PLAYERS,
            room.gameState.status
        )

        gameService.handleJoin(
            room.gameState,
            "P4",
            "session-4"
        )

        assertEquals(
            GameStatus.IN_PROGRESS,
            room.gameState.status
        )
    }

    @Test
    fun `triad outpost turn stays after only one move`() {

        val roomRegistry = RoomRegistry()
        val gameService = GameService(CombatService())

        val room = roomRegistry.createRoom(GameMode.TRIAD_OUTPOST)

        gameService.handleJoin(room.gameState, "P1", "session-1")
        gameService.handleJoin(room.gameState, "P2", "session-2")
        gameService.handleJoin(room.gameState, "P3", "session-3")

        assertEquals("P1", room.gameState.currentTurn)

        // P1 bewegt nur INFANTRY -- die anderen 2 Einheiten bleiben unbewegt.
        val state = gameService.handleMove(
            room.gameState,
            Move(
                player = "P1",
                type = UnitType.INFANTRY,
                fromX = 4,
                fromY = 9,
                toX = 4,
                toY = 10
            )
        )

        // Turn bleibt bei P1, weil ARCHER und CAVALRY noch nicht gezogen haben.
        assertEquals("P1", state.currentTurn)
    }

    @Test
    fun `battlefield peaks turn stays after only one move`() {

        val roomRegistry = RoomRegistry()
        val gameService = GameService(CombatService())

        val room = roomRegistry.createRoom(GameMode.BATTLEFIELD_PEAKS)

        gameService.handleJoin(room.gameState, "P1", "session-1")
        gameService.handleJoin(room.gameState, "P2", "session-2")
        gameService.handleJoin(room.gameState, "P3", "session-3")
        gameService.handleJoin(room.gameState, "P4", "session-4")

        assertEquals("P1", room.gameState.currentTurn)

        // P1-INFANTRY-Start: (6,9), Basis bei (6,10). Move zu (6,8) ist Border.
        val state = gameService.handleMove(
            room.gameState,
            Move(
                player = "P1",
                type = UnitType.INFANTRY,
                fromX = 6,
                fromY = 9,
                toX = 6,
                toY = 8
            )
        )

        // Turn bleibt bei P1, weil ARCHER und CAVALRY noch nicht gezogen haben.
        assertEquals("P1", state.currentTurn)
    }

    @Test
    fun `triad outpost switches turn after all three units moved`() {

        val roomRegistry = RoomRegistry()
        val gameService = GameService(CombatService())

        val room = roomRegistry.createRoom(GameMode.TRIAD_OUTPOST)

        gameService.handleJoin(room.gameState, "P1", "session-1")
        gameService.handleJoin(room.gameState, "P2", "session-2")
        gameService.handleJoin(room.gameState, "P3", "session-3")

        // P1-Start-Positionen (siehe GameService.startTriadOutpostGame):
        //   ARCHER (5,8), INFANTRY (4,9), CAVALRY (6,9), Basis (5,9)
        gameService.handleMove(
            room.gameState,
            Move("P1", UnitType.INFANTRY, 4, 9, 4, 10)
        )
        assertEquals("P1", room.gameState.currentTurn)

        gameService.handleMove(
            room.gameState,
            Move("P1", UnitType.ARCHER, 5, 8, 5, 7)
        )
        assertEquals("P1", room.gameState.currentTurn)

        // Mit dem dritten Move sollte der Auto-Switch greifen.
        val state = gameService.handleMove(
            room.gameState,
            Move("P1", UnitType.CAVALRY, 6, 9, 6, 10)
        )

        assertEquals("P2", state.currentTurn)
    }

    @Test
    fun `triad outpost endTurn forces switch with units remaining`() {

        val roomRegistry = RoomRegistry()
        val gameService = GameService(CombatService())

        val room = roomRegistry.createRoom(GameMode.TRIAD_OUTPOST)

        gameService.handleJoin(room.gameState, "P1", "session-1")
        gameService.handleJoin(room.gameState, "P2", "session-2")
        gameService.handleJoin(room.gameState, "P3", "session-3")

        // P1 bewegt nur INFANTRY, ARCHER und CAVALRY bleiben.
        gameService.handleMove(
            room.gameState,
            Move("P1", UnitType.INFANTRY, 4, 9, 4, 10)
        )
        assertEquals("P1", room.gameState.currentTurn)

        // Trotz unbewegter Einheiten beendet P1 manuell seinen Zug.
        val state = gameService.endTurn(room.gameState, "P1")

        assertEquals("P2", state.currentTurn)
    }

    @Test
    fun `battlefield peaks switches turn after all three units moved`() {

        val roomRegistry = RoomRegistry()
        val gameService = GameService(CombatService())

        val room = roomRegistry.createRoom(GameMode.BATTLEFIELD_PEAKS)

        gameService.handleJoin(room.gameState, "P1", "session-1")
        gameService.handleJoin(room.gameState, "P2", "session-2")
        gameService.handleJoin(room.gameState, "P3", "session-3")
        gameService.handleJoin(room.gameState, "P4", "session-4")

        // P1-Start-Positionen (siehe GameService.startBattlefieldPeaksGame):
        //   ARCHER (5,9), INFANTRY (6,9), CAVALRY (7,9), Basis (6,10)
        gameService.handleMove(
            room.gameState,
            Move("P1", UnitType.INFANTRY, 6, 9, 6, 8)
        )
        assertEquals("P1", room.gameState.currentTurn)

        gameService.handleMove(
            room.gameState,
            Move("P1", UnitType.ARCHER, 5, 9, 5, 10)
        )
        assertEquals("P1", room.gameState.currentTurn)

        // Mit dem dritten Move sollte der Auto-Switch greifen.
        val state = gameService.handleMove(
            room.gameState,
            Move("P1", UnitType.CAVALRY, 7, 9, 7, 10)
        )

        assertEquals("P2", state.currentTurn)
    }

}