package at.aau.hexabrawl.websocketserver.model

import kotlin.math.abs

/**
 * Hex-Grid Distanz-Berechnung fuer "odd-q offset" Koordinaten
 * (gleiches Schema wie im Frontend HexGridLogic verwendet).
 *
 * Eine direkte Manhattan-Distanz (|dx| + |dy|) waere falsch, weil
 * Hex-Felder versetzt liegen. Stattdessen konvertieren wir in cube
 * coordinates und berechnen dort die Distanz korrekt.
 *
 * Pure object, ohne Seiteneffekte oder State.
 */
object HexDistance {

    /**
     * Distanz zwischen zwei Hex-Feldern in "odd-q offset" Koordinaten.
     *
     * @param fromX Spalte des Startfelds
     * @param fromY Zeile des Startfelds
     * @param toX Spalte des Zielfelds
     * @param toY Zeile des Zielfelds
     * @return Anzahl der Hex-Schritte zwischen den Feldern (>= 0)
     */
    fun between(fromX: Int, fromY: Int, toX: Int, toY: Int): Int {
        val (fromXCube, fromYCube, fromZCube) = oddQToCube(fromX, fromY)
        val (toXCube, toYCube, toZCube) = oddQToCube(toX, toY)
        return (abs(fromXCube - toXCube) +
                abs(fromYCube - toYCube) +
                abs(fromZCube - toZCube)) / 2
    }

    /**
     * Konvertiert "odd-q offset" Koordinaten in cube coordinates.
     * In odd-q offset sind ungerade Spalten nach unten verschoben.
     */
    private fun oddQToCube(col: Int, row: Int): Triple<Int, Int, Int> {
        val x = col
        val z = row - (col - (col and 1)) / 2
        val y = -x - z
        return Triple(x, y, z)
    }
}
