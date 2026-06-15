package at.aau.hexabrawl.websocketserver.service

import at.aau.hexabrawl.websocketserver.model.GameState
import at.aau.hexabrawl.websocketserver.model.UnitType
import org.springframework.stereotype.Service

/**
 * Berechnet Hex-Connectivity zwischen den Feldern eines Spielers und seiner BASE.
 *
 * Felder, die per Hex-Nachbarschaft nicht mehr mit der BASE verbunden sind,
 * werden zu SKELETON-Feldern. Einheiten darauf (ausser BASE und SKELETON)
 * werden zu UnitType.SKELETON.
 *
 * Spieler ohne BASE-Unit werden uebersprungen — der WinCondition-Check
 * kuemmert sich um deren Ausscheiden.
 */
@Service
class ConnectivityService {

    companion object {
        /** Liefert die 6 Nachbarfelder eines Hex-Feldes in "odd-q offset" Koordinaten. */
        fun hexNeighbors(x: Int, y: Int): List<Pair<Int, Int>> =
            if (x % 2 == 0)
                listOf(x - 1 to y - 1, x - 1 to y, x to y - 1, x to y + 1, x + 1 to y - 1, x + 1 to y)
            else
                listOf(x - 1 to y, x - 1 to y + 1, x to y - 1, x to y + 1, x + 1 to y, x + 1 to y + 1)
    }

    /**
     * Prueft fuer alle Spieler, ob ihre Felder noch ueber Hex-Nachbarn mit ihrer
     * BASE verbunden sind. Markiert isolierte Felder als SKELETON.
     */
    fun recomputeConnectivity(state: GameState) {
        state.players.forEach { player ->
            val baseUnit = state.units.firstOrNull {
                it.player == player.name && it.type == UnitType.BASE
            } ?: return@forEach

            val connected = bfsConnectedFields(state, player.name, baseUnit.x, baseUnit.y)

            state.fields.filter { it.owner == player.name && !it.isSkeleton }.forEach { field ->
                if ((field.x to field.y) !in connected) {
                    field.isSkeleton = true
                    state.units.filter {
                        it.x == field.x && it.y == field.y &&
                                it.player == player.name &&
                                it.type != UnitType.BASE &&
                                it.type != UnitType.SKELETON
                    }.forEach { it.type = UnitType.SKELETON }
                }
            }
        }
    }

    private fun bfsConnectedFields(
        state: GameState,
        playerName: String,
        startX: Int,
        startY: Int
    ): Set<Pair<Int, Int>> {
        val visited = mutableSetOf(startX to startY)
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(startX to startY)

        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeFirst()
            for ((nx, ny) in hexNeighbors(x, y)) {
                if ((nx to ny) in visited) continue
                val field = state.fields.firstOrNull { it.x == nx && it.y == ny } ?: continue
                if (field.owner != playerName) continue
                if (field.isSkeleton) continue
                visited.add(nx to ny)
                queue.add(nx to ny)
            }
        }
        return visited
    }
}