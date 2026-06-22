package at.aau.hexabrawl.websocketserver

import at.aau.hexabrawl.websocketserver.service.BoardService
import at.aau.hexabrawl.websocketserver.service.CheatGiftService
import at.aau.hexabrawl.websocketserver.service.EconomyService
import at.aau.hexabrawl.websocketserver.service.CombatService
import at.aau.hexabrawl.websocketserver.service.ConnectivityService
import at.aau.hexabrawl.websocketserver.service.PlayerService
import at.aau.hexabrawl.websocketserver.service.TurnService


object TestServiceFactory {

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
