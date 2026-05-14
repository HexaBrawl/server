package at.aau.hexabrawl.websocketserver.model

enum class UnitType {
    ARCHER,
    INFANTRY,
    CAVALRY,
    SKELETON;


companion object {
    val BEATS = mapOf(INFANTRY to CAVALRY, CAVALRY to ARCHER, ARCHER to INFANTRY)
}

fun beats(other: UnitType) = BEATS[this] == other
}
