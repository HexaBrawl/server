package at.aau.hexabrawl.websocketserver.model

import com.fasterxml.jackson.annotation.JsonIgnore

/**
 * Represents a single game room with its own isolated game state.
 *
 * @property roomId Unique identifier for this room (UUID format).
 * @property joinCode Short 6-character code players can use to join.
 * @property mode The game mode determining max players and rules.
 * @property state The isolated game state for this room.
 */
data class Room(
    val roomId: String,
    val joinCode: String,
    val mode: GameMode,
    val gameState: GameState = GameState()
) {
    /** Current game status derived from the room's game state. */
    val status: GameStatus get() = gameState.status

    /** List of player names currently in this room. */
    val players: List<String> get() = gameState.players.map { it.name }

    /** Maximum number of players allowed in this room. */
    val maxPlayers: Int get() = mode.maxPlayers
}