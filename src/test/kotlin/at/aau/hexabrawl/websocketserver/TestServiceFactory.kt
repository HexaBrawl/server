package at.aau.hexabrawl.websocketserver

import at.aau.hexabrawl.websocketserver.service.BoardService
import at.aau.hexabrawl.websocketserver.service.CheatGiftService
import at.aau.hexabrawl.websocketserver.service.EconomyService
import at.aau.hexabrawl.websocketserver.service.CombatService
import at.aau.hexabrawl.websocketserver.service.GameService
import at.aau.hexabrawl.websocketserver.service.ConnectivityService
import at.aau.hexabrawl.websocketserver.service.PlayerService
import at.aau.hexabrawl.websocketserver.service.TurnService


/**
 * Baut einen voll-konfigurierten GameService fuer Unit-Tests.
 *
 * Bei jedem Refactoring-Schritt von GameService musst du NUR diese Datei
 * anpassen — die Tests selbst rufen ueber [createGameService] auf und
 * bleiben unveraendert.
 */
object TestServiceFactory {

    fun createGameService(): GameService {
        val combatService = CombatService()
        val connectivityService = ConnectivityService()
        val economyService = EconomyService()
        val cheatGiftService = CheatGiftService()
        val boardService = BoardService()
        val playerService = PlayerService(boardService)
        val turnService = TurnService(combatService, connectivityService, economyService, playerService)
        return GameService(
            connectivityService,
            economyService,
            cheatGiftService,
            boardService,
            playerService,
            turnService)
    }

    fun createTurnService(): TurnService {
        val boardService = BoardService()
        return TurnService(
            CombatService(),
            ConnectivityService(),
            EconomyService(),
            PlayerService(boardService)
        )
    }

    fun createPlayerService(): PlayerService = PlayerService(BoardService())

    fun createCheatGiftService(): CheatGiftService = CheatGiftService()
}
