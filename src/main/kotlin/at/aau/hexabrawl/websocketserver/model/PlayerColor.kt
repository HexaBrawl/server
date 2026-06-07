package at.aau.hexabrawl.websocketserver.model

/**
 * Represents the color assigned to a player.
 *
 * Colors are assigned automatically when players join a game.
 * Each player receives a unique color that can be used for
 * identification in multiplayer matches.
 */
enum class PlayerColor {
    RED,
    BLUE,
    GREEN,   // Für Spieler 3
    YELLOW   // Für Spieler 4
}
