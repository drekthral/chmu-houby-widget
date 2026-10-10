package cz.drekthral.houby

import kotlin.math.abs

/** Stupně z legendy mapy ČHMÚ „Pravděpodobnost růstu mykorhizních hub“. */
enum class HoubyLevel(val value: Int, val label: String, val color: Int, val darkText: Boolean) {
    VERY_LOW(1, "velmi nízká", 0xFFD93629.toInt(), darkText = false),
    LOW(2, "nízká", 0xFFF88D51.toInt(), darkText = true),
    MEDIUM(3, "střední", 0xFFFFFFBF.toInt(), darkText = true),
    HIGH(4, "vysoká", 0xFF86CB66.toInt(), darkText = true),
    VERY_HIGH(5, "velmi vysoká", 0xFF219B51.toInt(), darkText = false);

    companion object {
        fun of(value: Int): HoubyLevel? = entries.firstOrNull { it.value == value }

        /** Přiřadí barvu pixelu ke stupni legendy, nebo null, pokud žádnému neodpovídá. */
        fun fromPixel(argb: Int): HoubyLevel? {
            if ((argb ushr 24) < 128) return null
            return entries
                .map { it to colorDistance(argb, it.color) }
                .filter { it.second <= 40 }
                .minByOrNull { it.second }
                ?.first
        }

        private fun colorDistance(a: Int, b: Int): Int =
            abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) +
                abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) +
                abs((a and 0xFF) - (b and 0xFF))
    }
}
