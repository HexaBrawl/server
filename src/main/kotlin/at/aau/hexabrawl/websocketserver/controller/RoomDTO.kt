package at.aau.hexabrawl.websocketserver.controller

import at.aau.hexabrawl.websocketserver.model.GameMode
import at.aau.hexabrawl.websocketserver.model.Room

/**
 * Data Transfer Object for Room responses.
 * Prevents serialization issues with internal GameState lock.
 *
 * @property roomId Unique identifier of the room.
 * @property joinCode 6-character code for joining the room.
 * @property mode The game mode of the room.
 * @property maxPlayers Maximum number of players allowed.
 * @property currentPlayers Current number of players in the room.
 */
data class RoomDTO(
    val roomId: String,
    val joinCode: String,
    val mode: GameMode,
    val maxPlayers: Int,
    val currentPlayers: Int
)

/**
 * Extension function to convert a Room to a RoomDTO.
 *
 * @return RoomDTO representation of this room.
 */
fun Room.toDTO(): RoomDTO {
    return RoomDTO(
        roomId = this.roomId,
        joinCode = this.joinCode,
        mode = this.mode,
        maxPlayers = this.maxPlayers,
        currentPlayers = this.players.size
    )
}
