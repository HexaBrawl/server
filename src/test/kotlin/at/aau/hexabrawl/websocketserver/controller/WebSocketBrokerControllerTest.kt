package at.aau.hexabrawl.websocketserver.controller

import at.aau.hexabrawl.websocketserver.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.*
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.SimpMessagingTemplate
import at.aau.hexabrawl.websocketserver.TestServiceFactory
import at.aau.hexabrawl.websocketserver.service.EconomyService
import at.aau.hexabrawl.websocketserver.service.GameService
import at.aau.hexabrawl.websocketserver.service.TurnService


class WebSocketBrokerControllerTest {

    private lateinit var lobbyController: LobbyController
    private lateinit var gameTurnController: GameTurnController
    private lateinit var purchaseController: PurchaseController
    private lateinit var gameService: GameService
    private lateinit var economyService: EconomyService
    private lateinit var turnService: TurnService
    private lateinit var messagingTemplate: SimpMessagingTemplate // Neu für Issue #24
    private lateinit var headerAccessor: SimpMessageHeaderAccessor // Neu für Issue #24
    private lateinit var roomRegistry: RoomRegistry

    @BeforeEach
    fun setup() {
        gameService = TestServiceFactory.createGameService()
        economyService = EconomyService()
        turnService = TestServiceFactory.createTurnService()
        roomRegistry = RoomRegistry()
        messagingTemplate = mock(SimpMessagingTemplate::class.java) // Mock erstellen
        val contextResolver = GameContextResolver(roomRegistry, messagingTemplate)
        lobbyController = LobbyController(gameService, contextResolver, messagingTemplate)
        gameTurnController = GameTurnController(turnService, economyService, contextResolver, messagingTemplate)
        purchaseController = PurchaseController(economyService, contextResolver, messagingTemplate)

        headerAccessor = mock(SimpMessageHeaderAccessor::class.java)
        `when`(headerAccessor.sessionId).thenReturn("test-session")
    }

    /**
     * Helper: platziert ARCHER/INFANTRY/CAVALRY fuer beide DUAL_VALLEY-Spieler
     * auf den klassischen Test-Positionen. Wird seit dem Entfernen der
     * Start-Einheiten benoetigt, um Move-/Combat-Tests sauber aufzusetzen.
     */
    private fun seedDualValleyCombatUnits(state: GameState, p1: String, p2: String) {
        state.units.add(GameUnit(p1, 1, 2, UnitType.ARCHER))
        state.units.add(GameUnit(p1, 2, 3, UnitType.INFANTRY))
        state.units.add(GameUnit(p1, 3, 2, UnitType.CAVALRY))
        state.units.add(GameUnit(p2, 8, 7, UnitType.ARCHER))
        state.units.add(GameUnit(p2, 7, 8, UnitType.INFANTRY))
        state.units.add(GameUnit(p2, 6, 7, UnitType.CAVALRY))
    }

    @Test
    fun `player can join game`() {
        val state = gameService.handleJoin("Josef", "session-1")
        assertTrue(state.players.any { it.name == "Josef" })
        assertEquals(1, state.players.size)
    }

    @Test
    fun `duplicate player is not added`() {
        gameService.handleJoin("Josef", "session-1")
        val state = gameService.handleJoin("Josef", "session-1")

        assertEquals(1, state.players.size)
    }

    @Test
    fun `game starts when two players join`() {
        gameService.handleJoin("Josef", "session-1")
        val state = gameService.handleJoin("Sebastian", "session-1")

        assertEquals(2, state.players.size)
        assertNotNull(state.currentTurn)
        // Spieler starten nur mit Basis -- Kampfeinheiten werden gekauft.
        assertEquals(2, state.units.size)
        assertTrue(state.units.all { it.type == UnitType.BASE })
        assertEquals(GameStatus.IN_PROGRESS, state.status)
    }

    @Test
    fun `third player cannot join`() {
        gameService.handleJoin("Josef", "session-1")
        gameService.handleJoin("Sebastian", "session-2")
        val state = gameService.handleJoin("Gustav", "session-3")

        assertEquals(2, state.players.size)
    }

    @Test
    fun `move is rejected if game not started`() {
        val move = Move("Josef", UnitType.INFANTRY, 0, 0, 1, 1)

        val state = gameService.handleMove(move)

        assertNull(state.currentTurn)
    }

    @Test
    fun `wrong player cannot move`() {
        gameService.handleJoin("Josef", "session-1")
        gameService.handleJoin("Sebastian", "session-2")
        seedDualValleyCombatUnits(gameService.gameState, "Josef", "Sebastian")

        val move = Move("Sebastian", UnitType.INFANTRY, 5, 5, 6, 6)

        val state = gameService.handleMove(move)

        // Turn should still be Josef
        assertEquals("Josef", state.currentTurn)
    }

    @Test
    fun `player can move and turn switches`() {
        gameService.handleJoin("Josef", "session-1")
        gameService.handleJoin("Sebastian", "session-2")
        seedDualValleyCombatUnits(gameService.gameState, "Josef", "Sebastian")

        gameService.handleMove(Move("Josef", UnitType.ARCHER, 1, 2, 1, 3))
        gameService.handleMove(Move("Josef", UnitType.INFANTRY, 2, 3, 2, 4))
        gameService.handleMove(Move("Josef", UnitType.CAVALRY, 3, 2, 3, 3))
        val state = gameService.endTurn("Josef")

        val josefUnit = state.units.find {
            it.player == "Josef" && it.type == UnitType.INFANTRY
        }

        assertEquals(2, josefUnit?.x)
        assertEquals(4, josefUnit?.y)

        // Turn switched to Sebastian
        assertEquals("Sebastian", state.currentTurn)
    }

    @Test
    fun `move object is created correctly`() {
        val move = Move("Alice", UnitType.INFANTRY, 1, 1, 2, 2)

        assertEquals("Alice", move.player)
        assertEquals(UnitType.INFANTRY, move.type)
        assertEquals(1, move.fromX)
        assertEquals(2, move.toX)
    }

    @Test
    fun `game unit is initialized correctly`() {
        val unit = GameUnit("Alice", 2, 3, UnitType.INFANTRY)

        assertEquals("Alice", unit.player)
        assertEquals(2, unit.x)
        assertEquals(3, unit.y)
    }

    @Test
    fun `game state initializes empty`() {
        val state = GameState()

        assertTrue(state.players.isEmpty())
        assertTrue(state.units.isEmpty())
        assertNull(state.currentTurn)
    }

    @Test
    fun `multiple moves update unit positions correctly`() {
        gameService.handleJoin("Alice", "session-1")
        gameService.handleJoin("Bob", "session-2")
        seedDualValleyCombatUnits(gameService.gameState, "Alice", "Bob")

        // Gold geben, damit sie nach der Runde nicht pleitegehen
        gameService.getCurrentState().players.forEach { it.gold = 100 }

        // Alice bewegt alle 3 bewegbaren Einheiten - dann ist Bob dran.
        gameService.handleMove(Move("Alice", UnitType.ARCHER, 1, 2, 1, 3))
        gameService.handleMove(Move("Alice", UnitType.INFANTRY, 2, 3, 2, 4))
        gameService.handleMove(Move("Alice", UnitType.CAVALRY, 3, 2, 3, 3))
        gameService.endTurn("Alice")

        // Bob bewegt alle 3 bewegbaren Einheiten
        gameService.handleMove(Move("Bob", UnitType.ARCHER, 8, 7, 8, 8))
        gameService.handleMove(Move("Bob", UnitType.INFANTRY, 7, 8, 6, 8))
        gameService.handleMove(Move("Bob", UnitType.CAVALRY, 6, 7, 5, 7))
        val result = gameService.endTurn("Bob")

        val aliceUnit = result.units.find {
            it.player == "Alice" && it.type == UnitType.INFANTRY
        }

        val bobUnit = result.units.find {
            it.player == "Bob" && it.type == UnitType.INFANTRY
        }

        assertEquals(2, aliceUnit?.x)
        assertEquals(4, aliceUnit?.y)
        assertEquals(6, bobUnit?.x)
        assertEquals(8, bobUnit?.y)
    }

    @Test
    fun `move does nothing when wrong player`() {
        gameService.handleJoin("Alice", "session-1")
        gameService.handleJoin("Bob", "session-2")
        seedDualValleyCombatUnits(gameService.gameState, "Alice", "Bob")

        val result = gameService.handleMove(Move("Bob", UnitType.INFANTRY, 7, 8, 7, 6))

        val bobUnit = result.units.find { it.player == "Bob" && it.type == UnitType.INFANTRY }

        // Position should NOT change (not Bob's turn)
        assertEquals(7, bobUnit?.x)
        assertEquals(8, bobUnit?.y)
    }

    @Test
    fun `move ignored when game not started`() {
        val result = gameService.handleMove(Move("Alice", UnitType.INFANTRY, 0, 0, 1, 1))

        assertTrue(result.units.isEmpty())
    }


    @Test
    fun `game stays waiting when only one player joins`() {

        val room = roomRegistry.createRoom(
            GameMode.DUAL_VALLEY
        )

        val localHeaderAccessor = SimpMessageHeaderAccessor.create()

        val state = lobbyController.joinRoom(
            room.roomId,
            JoinRequest(name = "Alice"),
            localHeaderAccessor
        )!!

        assertEquals(1, state.players.size)
        assertEquals(GameStatus.WAITING_FOR_PLAYERS, state.status)
        assertNull(state.currentTurn)
        assertTrue(state.units.isEmpty())
    }

    @Test
    fun `move rejected when not players turn`() {
        gameService.handleJoin("Alice", "session-1")
        gameService.handleJoin("Bob", "session-2")

        val move = Move(player = "Bob", toX = 1, toY = 1)

        val state = gameService.handleMove(move)

        assertEquals("Alice", state.currentTurn)
    }

    @Test
    fun `turn switches after valid move`() {
        gameService.handleJoin("Alice", "session-1")
        gameService.handleJoin("Bob", "session-2")
        seedDualValleyCombatUnits(gameService.gameState, "Alice", "Bob")

        // Gold geben, damit sie nach der Runde nicht pleitegehen
        gameService.getCurrentState().players.forEach { it.gold = 100 }

        // Alice bewegt alle 3 Einheiten und beendet manuell ihren Zug -
        // dann ist Bob dran.
        gameService.handleMove(Move("Alice", UnitType.ARCHER, 1, 2, 1, 3))
        gameService.handleMove(Move("Alice", UnitType.INFANTRY, 2, 3, 2, 4))
        gameService.handleMove(Move("Alice", UnitType.CAVALRY, 3, 2, 3, 3))
        val state1 = gameService.endTurn("Alice")
        assertEquals("Bob", state1.currentTurn)

        // Bob bewegt alle 3 Einheiten und beendet manuell - dann ist Alice dran.
        gameService.handleMove(Move("Bob", UnitType.ARCHER, 8, 7, 8, 8))
        gameService.handleMove(Move("Bob", UnitType.INFANTRY, 7, 8, 6, 8))
        gameService.handleMove(Move("Bob", UnitType.CAVALRY, 6, 7, 5, 7))
        val state2 = gameService.endTurn("Bob")
        assertEquals("Alice", state2.currentTurn)
    }

    @Test
    fun `room state contains joined players`() {

        val room = roomRegistry.createRoom(
            GameMode.DUAL_VALLEY
        )

        lobbyController.joinRoom(
            room.roomId,
            JoinRequest(name = "Josef"),
            headerAccessor
        )

        lobbyController.joinRoom(
            room.roomId,
            JoinRequest(name = "Marie"),
            headerAccessor
        )

        assertEquals(
            2,
            room.gameState.players.size
        )
    }

    @Test
    fun `join stores sessionId`() {
        val state = gameService.handleJoin(
            "Alice",
            "session-1"
        )

        assertEquals(
            "session-1",
            state.players[0].sessionId
        )
    }

    @Test
    fun `join with empty sessionId still adds player`() {
        val state = gameService.handleJoin(
            "Alice",
            ""
        )

        assertEquals(1, state.players.size)
        assertEquals("Alice", state.players[0].name)
        assertEquals("", state.players[0].sessionId)
    }


    @Test
    fun `join uses empty sessionId when header sessionId is null`() {

        val room = roomRegistry.createRoom(
            GameMode.DUAL_VALLEY
        )

        val localHeaderAccessor = mock(SimpMessageHeaderAccessor::class.java)

        `when`(localHeaderAccessor.sessionId).thenReturn(null)

        val state = lobbyController.joinRoom(
            room.roomId,
            JoinRequest(name = "Alice"),
            localHeaderAccessor
        )!!

        assertEquals(
            "",
            state.players[0].sessionId
        )
    }


    @Test
    fun `player can join game with sessionId`() {

        val room = roomRegistry.createRoom(
            GameMode.DUAL_VALLEY
        )

        val localHeaderAccessor = mock(SimpMessageHeaderAccessor::class.java)

        `when`(localHeaderAccessor.sessionId).thenReturn("session-1")

        val state = lobbyController.joinRoom(
            room.roomId,
            JoinRequest(name = "Josef"),
            localHeaderAccessor
        )!!

        assertTrue(
            state.players.any { it.name == "Josef" }
        )

        assertEquals(1, state.players.size)

        assertEquals(
            "session-1",
            state.players[0].sessionId
        )
    }


    @Test
    fun `join via websocket sends GAME_FULL error when full`() {

        val room = roomRegistry.createRoom(
            GameMode.DUAL_VALLEY
        )

        lobbyController.joinRoom(
            room.roomId,
            JoinRequest(name = "P1", color = PlayerColor.RED),
            headerAccessor
        )

        lobbyController.joinRoom(
            room.roomId,
            JoinRequest(name = "P2", color = PlayerColor.BLUE),
            headerAccessor
        )

        val result = lobbyController.joinRoom(
            room.roomId,
            JoinRequest(name = "P3", color = PlayerColor.GREEN),
            headerAccessor
        )

        assertNull(result)

        verify(messagingTemplate).convertAndSendToUser(
            eq("test-session"),
            eq("/queue/errors"),
            argThat {
                it is ErrorMessage &&
                        it.errorCode == ErrorCode.GAME_FULL
            }
        )
    }

    @Test
    fun `move via websocket sends GAME_NOT_STARTED error`() {

        val room = roomRegistry.createRoom(
            GameMode.DUAL_VALLEY
        )

        val move = Move(player = "P1")

        val result = gameTurnController.moveRoom(
            room.roomId,
            move,
            headerAccessor
        )

        assertNull(result)

        verify(messagingTemplate).convertAndSendToUser(
            eq("test-session"),
            eq("/queue/errors"),
            argThat {
                it is ErrorMessage &&
                        it.errorCode == ErrorCode.GAME_NOT_STARTED
            }
        )
    }

    @Test
    fun `move via websocket sends NOT_YOUR_TURN error`() {

        val room = roomRegistry.createRoom(
            GameMode.DUAL_VALLEY
        )

        lobbyController.joinRoom(
            room.roomId,
            JoinRequest(name = "P1"),
            headerAccessor
        )

        lobbyController.joinRoom(
            room.roomId,
            JoinRequest(name = "P2"),
            headerAccessor
        )

        val move = Move(player = "P2")

        val result = gameTurnController.moveRoom(
            room.roomId,
            move,
            headerAccessor
        )

        assertNull(result)

        verify(messagingTemplate).convertAndSendToUser(
            eq("test-session"),
            eq("/queue/errors"),
            argThat {
                it is ErrorMessage &&
                        it.errorCode == ErrorCode.NOT_YOUR_TURN
            }
        )
    }


    @Test
    fun `move via websocket sends INVALID_MOVE error`() {

        val room = roomRegistry.createRoom(
            GameMode.DUAL_VALLEY
        )

        lobbyController.joinRoom(
            room.roomId,
            JoinRequest(name = "P1"),
            headerAccessor
        )

        lobbyController.joinRoom(
            room.roomId,
            JoinRequest(name = "P2"),
            headerAccessor
        )

        val move = Move(
            player = "P1",
            fromX = 0,
            fromY = 0,
            toX = 9,
            toY = 9
        )

        val result = gameTurnController.moveRoom(
            room.roomId,
            move,
            headerAccessor
        )

        assertNull(result)

        verify(messagingTemplate).convertAndSendToUser(
            eq("test-session"),
            eq("/queue/errors"),
            argThat {
                it is ErrorMessage &&
                        it.errorCode == ErrorCode.INVALID_MOVE
            }
        )
    }


    @Test
    fun `move via websocket rejected when game status is FINISHED`() {
        gameService.handleJoin("Alice", "session-1")
        gameService.handleJoin("Bob", "session-2")

        gameService.gameState.status = GameStatus.FINISHED
    }

    @Test
    fun `broadcasts new state on success`() {

        val room = roomRegistry.createRoom(
            GameMode.DUAL_VALLEY
        )

        lobbyController.joinRoom(
            room.roomId,
            JoinRequest(name = "Alice"),
            headerAccessor
        )

        lobbyController.joinRoom(
            room.roomId,
            JoinRequest(name = "Bob"),
            headerAccessor
        )

        seedDualValleyCombatUnits(room.gameState, "Alice", "Bob")

        val move = Move(
            player = "Alice",
            type = UnitType.INFANTRY,
            fromX = 2,
            fromY = 3,
            toX = 2,
            toY = 4
        )

        // Join-Broadcasts ignorieren
        clearInvocations(messagingTemplate)

        val result = gameTurnController.moveRoom(
            room.roomId,
            move,
            headerAccessor
        )

        assertNotNull(result)

        verify(messagingTemplate).convertAndSend(
            eq("/topic/rooms/${room.roomId}/state"),
            eq(result!!)
        )
    }


    @Test
    fun `valid move switches turn`() {

        val room = roomRegistry.createRoom(
            GameMode.DUAL_VALLEY
        )

        lobbyController.joinRoom(
            room.roomId,
            JoinRequest(name = "Alice"),
            headerAccessor
        )

        lobbyController.joinRoom(
            room.roomId,
            JoinRequest(name = "Bob"),
            headerAccessor
        )

        seedDualValleyCombatUnits(room.gameState, "Alice", "Bob")

        // Alle 3 bewegbaren Einheiten bewegen, dann manuell Runde beenden.
        gameTurnController.moveRoom(room.roomId, Move("Alice", UnitType.ARCHER, 1, 2, 1, 3), headerAccessor)
        gameTurnController.moveRoom(room.roomId, Move("Alice", UnitType.INFANTRY, 2, 3, 2, 4), headerAccessor)
        gameTurnController.moveRoom(
            room.roomId,
            Move("Alice", UnitType.CAVALRY, 3, 2, 3, 3),
            headerAccessor
        )
        val result = gameTurnController.endTurnRoom(
            room.roomId,
            EndTurnRequest(playerName = "Alice"),
            headerAccessor
        )

        assertNotNull(result)

        assertEquals(
            "Bob",
            result?.currentTurn
        )
    }

    @Test
    fun `initRoom returns null for invalid room id`() {
        val result = lobbyController.initRoom("invalid-room-id", headerAccessor)

        assertNull(result)
    }

    @Test
    fun `initRoom returns state of requested room`() {

        val room = roomRegistry.createRoom(GameMode.DUAL_VALLEY)

        room.gameState.players.add(
            Player("Josef", "session1", PlayerColor.RED)
        )

        val result = lobbyController.initRoom(room.roomId, headerAccessor)

        assertNotNull(result)
        assertEquals(1, result!!.players.size)
        assertEquals("Josef", result.players[0].name)
    }

    @Test
    fun `joinRoom returns null for invalid room id`() {

        val headerAccessor = SimpMessageHeaderAccessor.create()

        val result = lobbyController.joinRoom(
            "invalid-room-id",
            JoinRequest(name = "Josef"),
            headerAccessor
        )

        assertNull(result)
    }

    @Test
    fun `joinRoom adds player to requested room`() {

        val room = roomRegistry.createRoom(
            GameMode.DUAL_VALLEY
        )

        val headerAccessor = SimpMessageHeaderAccessor.create()

        lobbyController.joinRoom(
            room.roomId,
            JoinRequest(name = "Josef"),
            headerAccessor
        )

        assertEquals(
            1,
            room.gameState.players.size
        )

        assertEquals(
            "Josef",
            room.gameState.players[0].name
        )
    }

    @Test
    fun `moveRoom returns null for invalid room id`() {

        val headerAccessor = SimpMessageHeaderAccessor.create()

        val move = Move(
            player = "Josef",
            type = UnitType.INFANTRY,
            fromX = 0,
            fromY = 0,
            toX = 1,
            toY = 0
        )

        val result = gameTurnController.moveRoom(
            "invalid-room-id",
            move,
            headerAccessor
        )

        assertNull(result)
    }

    @Test
    fun `moveRoom returns null when game not started`() {

        val room = roomRegistry.createRoom(
            GameMode.DUAL_VALLEY
        )

        val headerAccessor = SimpMessageHeaderAccessor.create()

        val move = Move(
            player = "Josef",
            type = UnitType.INFANTRY,
            fromX = 0,
            fromY = 0,
            toX = 1,
            toY = 0
        )

        val result = gameTurnController.moveRoom(
            room.roomId,
            move,
            headerAccessor
        )

        assertNull(result)
    }

    @Test
    fun `initRoom broadcasts state to room topic`() {

        val room = roomRegistry.createRoom(
            GameMode.DUAL_VALLEY
        )

        lobbyController.initRoom(room.roomId, headerAccessor)

        verify(messagingTemplate).convertAndSend(
            "/topic/rooms/${room.roomId}/state",
            room.gameState
        )
    }

    @Test
    fun `joinRoom broadcasts state to room topic`() {

        val room = roomRegistry.createRoom(
            GameMode.DUAL_VALLEY
        )

        val headerAccessor = SimpMessageHeaderAccessor.create()

        lobbyController.joinRoom(
            room.roomId,
            JoinRequest(name = "Josef"),
            headerAccessor
        )

        verify(messagingTemplate).convertAndSend(
            "/topic/rooms/${room.roomId}/state",
            room.gameState
        )
    }

    @Test
    fun `moveRoom broadcasts state to room topic`() {

        val room = roomRegistry.createRoom(
            GameMode.DUAL_VALLEY
        )

        val headerAccessor = SimpMessageHeaderAccessor.create()

        gameService.handleJoin(
            room.gameState,
            "Josef",
            "session-1"
        )

        gameService.handleJoin(
            room.gameState,
            "Marie",
            "session-2"
        )
        seedDualValleyCombatUnits(room.gameState, "Josef", "Marie")

        val move = Move(
            player = "Josef",
            type = UnitType.INFANTRY,
            fromX = 2,
            fromY = 3,
            toX = 2,
            toY = 4
        )

        gameTurnController.moveRoom(
            room.roomId,
            move,
            headerAccessor
        )

        verify(messagingTemplate).convertAndSend(
            "/topic/rooms/${room.roomId}/state",
            room.gameState
        )
    }

    @Test
    fun `initRoom sends ROOM_NOT_FOUND for invalid room id`() {
        val headerAccessor = SimpMessageHeaderAccessor.create()

        lobbyController.initRoom(
            "invalid-room-id",
            headerAccessor
        )

        verify(messagingTemplate).convertAndSendToUser(
            anyString(),
            eq("/queue/errors"),
            eq(
                ErrorMessage(
                    ErrorCode.ROOM_NOT_FOUND,
                    "Raum nicht gefunden."
                )
            )
        )
    }

    @Test
    fun `joinRoom sends ROOM_NOT_FOUND for invalid room id`() {

        lobbyController.joinRoom(
            "invalid-room-id",
            JoinRequest(name = "Josef"),
            headerAccessor
        )

        verify(messagingTemplate).convertAndSendToUser(
            anyString(),
            eq("/queue/errors"),
            eq(
                ErrorMessage(
                    ErrorCode.ROOM_NOT_FOUND,
                    "Raum nicht gefunden."
                )
            )
        )
    }

    @Test
    fun `moveRoom sends ROOM_NOT_FOUND for invalid room id`() {

        val move = Move(
            player = "Josef",
            type = UnitType.INFANTRY,
            fromX = 0,
            fromY = 0,
            toX = 1,
            toY = 0
        )

        gameTurnController.moveRoom(
            "invalid-room-id",
            move,
            headerAccessor
        )

        verify(messagingTemplate).convertAndSendToUser(
            anyString(),
            eq("/queue/errors"),
            eq(
                ErrorMessage(
                    ErrorCode.ROOM_NOT_FOUND,
                    "Raum nicht gefunden."
                )
            )
        )
    }

    @Test
    fun `joinRoom sends GAME_FULL when room is full`() {

        val room = roomRegistry.createRoom(
            GameMode.DUAL_VALLEY
        )

        gameService.handleJoin(
            room.gameState,
            "Benno",
            "session-1"
        )

        gameService.handleJoin(
            room.gameState,
            "Josef",
            "session-2"
        )

        val result = lobbyController.joinRoom(
            room.roomId,
            JoinRequest(name = "Marie"),
            headerAccessor
        )

        assertNull(result)

        verify(messagingTemplate).convertAndSendToUser(
            anyString(),
            eq("/queue/errors"),
            eq(
                ErrorMessage(
                    ErrorCode.GAME_FULL,
                    "Beitritt verweigert: Spiel ist voll."
                )
            )
        )
    }

    @Test
    fun `moveRoom sends NOT_YOUR_TURN`() {

        val room = roomRegistry.createRoom(
            GameMode.DUAL_VALLEY
        )

        gameService.handleJoin(
            room.gameState,
            "Josef",
            "session-1"
        )

        gameService.handleJoin(
            room.gameState,
            "Marie",
            "session-2"
        )

        val move = Move(
            player = "Marie",
            type = UnitType.INFANTRY,
            fromX = 3,
            fromY = 2,
            toX = 3,
            toY = 3
        )

        val result = gameTurnController.moveRoom(
            room.roomId,
            move,
            headerAccessor
        )

        assertNull(result)

        verify(messagingTemplate).convertAndSendToUser(
            anyString(),
            eq("/queue/errors"),
            eq(
                ErrorMessage(
                    ErrorCode.NOT_YOUR_TURN,
                    "Es ist nicht dein Zug!"
                )
            )
        )
    }

    @Test
    fun `moveRoom sends INVALID_MOVE`() {

        val room = roomRegistry.createRoom(
            GameMode.DUAL_VALLEY
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

        val move = Move(
            player = "P1",
            fromX = 0,
            fromY = 0,
            toX = 9,
            toY = 9
        )

        val result = gameTurnController.moveRoom(
            room.roomId,
            move,
            headerAccessor
        )

        assertNull(result)

        verify(messagingTemplate).convertAndSendToUser(
            eq("test-session"),
            eq("/queue/errors"),
            argThat {
                it is ErrorMessage &&
                        it.errorCode == ErrorCode.INVALID_MOVE
            }
        )
    }

    @Test
    fun `joining final player broadcasts started game state`() {

        val room = roomRegistry.createRoom(
            GameMode.TRIAD_OUTPOST
        )

        lobbyController.joinRoom(
            room.roomId,
            JoinRequest(name = "P1", color = PlayerColor.RED),
            headerAccessor
        )

        lobbyController.joinRoom(
            room.roomId,
            JoinRequest(name = "P2", color = PlayerColor.BLUE),
            headerAccessor
        )

        val state = lobbyController.joinRoom(
            room.roomId,
            JoinRequest(name = "P3", color = PlayerColor.GREEN),
            headerAccessor
        )

        assertEquals(
            GameStatus.IN_PROGRESS,
            state?.status
        )

        verify(messagingTemplate, atLeastOnce())
            .convertAndSend(
                eq("/topic/rooms/${room.roomId}/state"),
                any(GameState::class.java)
            )
    }

    // ---- Tests fuer Sub-Issue #131 (Buy-Farm Room-Endpoint) ----

    @Test
    fun `buyFarmRoom happy path increases farms and calculates new price`() {
        val room = roomRegistry.createRoom(GameMode.DUAL_VALLEY)
        room.gameState.status = GameStatus.IN_PROGRESS
        val alice = Player("Alice", "test-session", gold = 25, farms = 0)
        room.gameState.players.add(alice)
        room.gameState.currentTurn = "Alice"

        val result1 = purchaseController.buyFarmRoom(room.roomId, headerAccessor)!!
        assertEquals(1, alice.farms)
        assertEquals(15, alice.gold)
        assertEquals(3, alice.income)
        verify(messagingTemplate).convertAndSend(eq("/topic/rooms/${room.roomId}/state"), eq(result1))

        val result2 = purchaseController.buyFarmRoom(room.roomId, headerAccessor)!!
        assertEquals(2, alice.farms)
        assertEquals(4, alice.gold)
        assertEquals(6, alice.income)
    }

    @Test
    fun `buyFarmRoom sends ROOM_NOT_FOUND if room does not exist`() {
        val result = purchaseController.buyFarmRoom("invalid-id", headerAccessor)
        assertNull(result)
        verify(messagingTemplate).convertAndSendToUser(
            eq("test-session"), eq("/queue/errors"),
            argThat { it is ErrorMessage && it.errorCode == ErrorCode.ROOM_NOT_FOUND }
        )
    }

    @Test
    fun `buyFarmRoom sends GAME_NOT_STARTED if status is WAITING`() {
        val room = roomRegistry.createRoom(GameMode.DUAL_VALLEY)
        room.gameState.players.add(Player("Alice", "test-session"))
        val result = purchaseController.buyFarmRoom(room.roomId, headerAccessor)
        assertNull(result)
    }

    @Test
    fun `buyFarmRoom sends NOT_YOUR_TURN if player is not currentTurn`() {
        val room = roomRegistry.createRoom(GameMode.DUAL_VALLEY)
        room.gameState.status = GameStatus.IN_PROGRESS
        room.gameState.players.add(Player("Alice", "test-session"))
        room.gameState.currentTurn = "Bob"
        val result = purchaseController.buyFarmRoom(room.roomId, headerAccessor)
        assertNull(result)
    }

    @Test
    fun `buyFarmRoom sends INSUFFICIENT_GOLD if player cannot afford farm`() {
        val room = roomRegistry.createRoom(GameMode.DUAL_VALLEY)
        room.gameState.status = GameStatus.IN_PROGRESS
        room.gameState.currentTurn = "Alice"
        room.gameState.players.add(Player("Alice", "test-session", gold = 9))
        val result = purchaseController.buyFarmRoom(room.roomId, headerAccessor)
        assertNull(result)
    }

    @Test
    fun `buyFarmRoom returns null if player is not found in room`() {
        val room = roomRegistry.createRoom(GameMode.DUAL_VALLEY)
        room.gameState.status = GameStatus.IN_PROGRESS
        val result = purchaseController.buyFarmRoom(room.roomId, headerAccessor)
        assertNull(result)
    }

    @Test
    fun `buyFarmRoom handles missing sessionId gracefully`() {
        val emptyAccessor = mock(SimpMessageHeaderAccessor::class.java)
        `when`(emptyAccessor.sessionId).thenReturn(null)
        val room = roomRegistry.createRoom(GameMode.DUAL_VALLEY)
        val result = purchaseController.buyFarmRoom(room.roomId, emptyAccessor)
        assertNull(result)
    }

    // ---- Color-/Reconnect-Tests fuer Sub-Issue #107 ----

    @Test
    fun `joinRoom allows reconnecting player even if game is max capacity`() {
        val room = roomRegistry.createRoom(GameMode.DUAL_VALLEY)

        lobbyController.joinRoom(
            room.roomId,
            JoinRequest(name = "Alice", color = PlayerColor.RED),
            headerAccessor
        )
        val secondHeaderAccessor = mock(SimpMessageHeaderAccessor::class.java)
        `when`(secondHeaderAccessor.sessionId).thenReturn("session-2")
        lobbyController.joinRoom(
            room.roomId,
            JoinRequest(name = "Bob", color = PlayerColor.BLUE),
            secondHeaderAccessor
        )

        // Alice joint neu mit anderer Session
        val reconnectHeaderAccessor = mock(SimpMessageHeaderAccessor::class.java)
        `when`(reconnectHeaderAccessor.sessionId).thenReturn("session-3")

        val state = lobbyController.joinRoom(
            room.roomId,
            JoinRequest(name = "Alice", color = PlayerColor.RED),
            reconnectHeaderAccessor
        )

        // Re-Join wird durchgewinkt, kein GAME_FULL
        assertNotNull(state)
        assertEquals(2, state?.players?.size)
    }

    @Test
    fun `joinRoom applies color from JoinRequest`() {
        val room = roomRegistry.createRoom(GameMode.DUAL_VALLEY)

        val state = lobbyController.joinRoom(
            room.roomId,
            JoinRequest(name = "Alice", color = PlayerColor.GREEN),
            headerAccessor
        )!!

        assertEquals(PlayerColor.GREEN, state.players[0].color)
    }

    @Test
    fun `joinRoom with duplicate color sends COLOR_ALREADY_TAKEN error`() {
        val room = roomRegistry.createRoom(GameMode.DUAL_VALLEY)

        lobbyController.joinRoom(
            room.roomId,
            JoinRequest(name = "Alice", color = PlayerColor.RED),
            headerAccessor
        )

        val secondHeaderAccessor = mock(SimpMessageHeaderAccessor::class.java)
        `when`(secondHeaderAccessor.sessionId).thenReturn("session-2")

        val result = lobbyController.joinRoom(
            room.roomId,
            JoinRequest(name = "Bob", color = PlayerColor.RED),
            secondHeaderAccessor
        )

        assertNull(result)
        verify(messagingTemplate).convertAndSendToUser(
            eq("session-2"),
            eq("/queue/errors"),
            argThat { it is ErrorMessage && it.errorCode == ErrorCode.COLOR_ALREADY_TAKEN }
        )
    }

    @Test
    fun `PlayerColor supports all four colors`() {
        assertEquals(4, PlayerColor.entries.size)
        assertTrue(
            PlayerColor.entries.containsAll(
                listOf(PlayerColor.RED, PlayerColor.BLUE, PlayerColor.GREEN, PlayerColor.YELLOW)
            )
        )
    }

    // ---- Tests fuer Move-Distanz-Validierung (Sub-Issue #102) -----------

    @Test
    fun `move further than 2 hex fields is rejected`() {
        gameService.handleJoin("Alice", "session-1")
        gameService.handleJoin("Bob", "session-2")
        seedDualValleyCombatUnits(gameService.gameState, "Alice", "Bob")

        // Alice INFANTRY steht auf (2, 3), Versuch auf (2, 8) - viel zu weit.
        val move = Move("Alice", UnitType.INFANTRY, 2, 3, 2, 8)
        val state = gameService.handleMove(move)

        // Position unveraendert.
        val infantry = state.units.first { it.player == "Alice" && it.type == UnitType.INFANTRY }
        assertEquals(2, infantry.x)
        assertEquals(3, infantry.y)

        // Turn switcht nicht.
        assertEquals("Alice", state.currentTurn)
    }

    @Test
    fun `move exactly 2 hex fields is accepted`() {
        gameService.handleJoin("Alice", "session-1")
        gameService.handleJoin("Bob", "session-2")
        seedDualValleyCombatUnits(gameService.gameState, "Alice", "Bob")

        // Alice bewegt alle 3 Einheiten - INFANTRY genau 2 Hex weit.
        gameService.handleMove(Move("Alice", UnitType.ARCHER, 1, 2, 1, 3))
        gameService.handleMove(Move("Alice", UnitType.INFANTRY, 2, 3, 4, 2))
        gameService.handleMove(Move("Alice", UnitType.CAVALRY, 3, 2, 3, 3))
        val state = gameService.endTurn("Alice")

        val infantry = state.units.first { it.player == "Alice" && it.type == UnitType.INFANTRY }
        assertEquals(4, infantry.x)
        assertEquals(2, infantry.y)

        // Turn ist gewechselt.
        assertEquals("Bob", state.currentTurn)
    }

    @Test
    fun `move to same field is rejected`() {
        gameService.handleJoin("Alice", "session-1")
        gameService.handleJoin("Bob", "session-2")

        // (3, 2) -> (3, 2): Distanz 0.
        val move = Move("Alice", UnitType.INFANTRY, 3, 2, 3, 2)
        val state = gameService.handleMove(move)

        assertEquals("Alice", state.currentTurn)
    }

    // ---- Tests fuer endTurn-Logik im GameService (Sub-Issue #105) -------
    //
    // Die frueheren controller.endTurn(playerName, headerAccessor)-Bridge-
    // Tests fuer NOT_YOUR_TURN und GAME_NOT_STARTED sind weggefallen, weil
    // die Bridge mit dem Controller-Split entfernt wurde. Die gleichwertige
    // Validierung im /end-turn-Endpoint wird in den endTurnRoom-Tests
    // gegen den GameTurnController abgedeckt.

    @Test
    fun `endTurn switches to next player`() {
        gameService.handleJoin("Alice", "session-1")
        gameService.handleJoin("Bob", "session-2")

        val result = gameService.endTurn("Alice")

        assertNotNull(result)
        assertEquals("Bob", result.currentTurn)
    }

    @Test
    fun `endTurn resets hasMovedThisTurn flags`() {
        gameService.handleJoin("Alice", "session-1")
        gameService.handleJoin("Bob", "session-2")

        // Alice bewegt INFANTRY (Flag wird gesetzt)
        gameService.handleMove(Move("Alice", UnitType.INFANTRY, 3, 2, 3, 3))

        // Alice beendet Runde freiwillig (CAVALRY und ARCHER noch nicht bewegt)
        gameService.endTurn("Alice")

        // Nach endTurn muessen alle Flags zurueckgesetzt sein.
        val state = gameService.getCurrentState()
        assertTrue(state.units.none { it.hasMovedThisTurn })
        assertEquals("Bob", state.currentTurn)
    }

    @Test
    fun `buyUnitRoom sends INVALID_PLACEMENT if target field is a skeleton`() {
        val room = roomRegistry.createRoom(GameMode.DUAL_VALLEY)
        room.gameState.status = GameStatus.IN_PROGRESS
        room.gameState.currentTurn = "Alice"

        val alice = Player("Alice", "test-session", gold = 50)
        room.gameState.players.add(alice)

        // Setup: Das Zielfeld gehört Alice, ist aber eine tote Zone (isSkeleton = true)
        room.gameState.fields.add(Field(x = 2, y = 2, owner = "Alice", isSkeleton = true))

        val request = BuyUnitRequest(
            playerName = "Alice",
            type = UnitType.INFANTRY,
            x = 2,
            y = 2
        )

        // Ausführung
        val result = purchaseController.buyUnitRoom(room.roomId, request, headerAccessor)

        // Assertions
        assertNull(result) // Kauf muss abgebrochen werden

        // Prüfen, dass das Gold NICHT abgezogen wurde
        assertEquals(50, alice.gold)

        // Prüfen, dass KEINE Einheit platziert wurde
        assertTrue(room.gameState.units.none { it.x == 2 && it.y == 2 && it.type == UnitType.INFANTRY })

        // Prüfen, dass die korrekte INVALID_PLACEMENT Fehlermeldung an den Client gesendet wurde
        verify(messagingTemplate).convertAndSendToUser(
            eq("test-session"),
            eq("/queue/errors"),
            argThat {
                it is ErrorMessage &&
                        it.errorCode == ErrorCode.INVALID_PLACEMENT
            }
        )
    }
}