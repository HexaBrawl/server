package at.aau.hexabrawl.websocketserver.model

/**
 * Defines the available game modes with their respective maximum player counts.
 *
 * @property maxPlayers Maximum number of players allowed in this game mode.
 */
enum class GameMode(val maxPlayers: Int) {
    /** 1v1 mode for two players. */
    DUAL_VALLEY(2),

    /** Three-player mode. */
    TRIAD_OUTPOST(3),

    /** Four-player mode. */
    BATTLEFIELD_PEAKS(4)
}