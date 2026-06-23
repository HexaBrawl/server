package at.aau.hexabrawl.websocketserver.model

/**
 * Alle Einheiten- und Feld-Typen.
 *
 * Combat-Stein-Schere-Papier: INFANTRY > CAVALRY > ARCHER > INFANTRY
 * (siehe [BEATS]). [SKELETON] markiert tote Ueberreste — koennen weder
 * angreifen noch angegriffen werden, werden bei Move ueber das Feld
 * absorbiert. [BASE] ist die nicht-bewegbare Heimat-Unit; ihr Verlust
 * bedeutet Ausscheiden des Spielers.
 */
enum class UnitType {
    ARCHER,
    INFANTRY,
    CAVALRY,
    SKELETON,
    BASE;


companion object {
    /** Rock-Paper-Scissors-Sieges-Map: Schlüssel schlägt Wert. */
    val BEATS = mapOf(INFANTRY to CAVALRY, CAVALRY to ARCHER, ARCHER to INFANTRY)
}

/**
 * Gibt true zurück, wenn dieser [UnitType] [other] im Kampf schlägt.
 * Basiert auf der [BEATS]-Map im Companion Object.
 */
fun beats(other: UnitType) = BEATS[this] == other
}
