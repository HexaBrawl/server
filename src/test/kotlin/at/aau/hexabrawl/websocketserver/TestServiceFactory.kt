package at.aau.hexabrawl.websocketserver

import at.aau.hexabrawl.websocketserver.service.CheatGiftService
import at.aau.hexabrawl.websocketserver.service.EconomyService
import at.aau.hexabrawl.websocketserver.service.CombatService
import at.aau.hexabrawl.websocketserver.service.GameService
import at.aau.hexabrawl.websocketserver.service.ConnectivityService


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
        return GameService(combatService, connectivityService, economyService, cheatGiftService)

    }
}
