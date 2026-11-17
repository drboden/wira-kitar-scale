package my.klean.scalebridge

import org.json.JSONObject

/** One weight reading as handed to the dashboard. Weight is always in kg when the indicator sends a unit. */
data class Reading(
    val weight: Double,
    val unit: String,
    val stable: Boolean,
    val raw: String,
    val timestamp: Long
) {
    val parsed: Boolean get() = !weight.isNaN()

    fun toJson(): JSONObject = JSONObject()
        .put("weight", if (parsed) Math.round(weight * 1000.0) / 1000.0 else JSONObject.NULL)
        .put("parsed", parsed)
        .put("unit", unit)
        .put("stable", stable)
        .put("raw", raw)
        .put("ts", timestamp)
        .put("source", "scale")
}

/**
 * Turns one line of text from the weighing indicator into a weight.
 *
 * Handles the common indicator formats, for example:
 *   ST,GS,+0012.34kg      (ST = stable, US = unstable, GS = gross, NT = net)
 *   +   12.34 kg
 *   GROSS:  12.34kg
 * Date/time lines from print mode are ignored because they carry no unit.
 * If the 3SM sends something unusual, set a custom pattern in Settings.
 */
object WeightParser {

    data class Parsed(val weight: Double, val unit: String, val stableFlag: Boolean?)

    private val numberWithUnit =
        Regex("""([+-])?\s*(\d+(?:[.,]\d+)?)\s*(kg|lb|oz|g)?""", RegexOption.IGNORE_CASE)
    private val unstableToken = Regex("""\b(US|MO|MOTION|UNSTABLE)\b""")
    private val stableToken = Regex("""\b(ST|STABLE)\b""")

    fun parse(line: String, s: Settings): Parsed? {
        val custom = s.customRegex.trim()
        if (custom.isNotEmpty()) {
            val m = try { Regex(custom).find(line) } catch (e: Exception) { null } ?: return null
            val text = (m.groups[1]?.value ?: m.value).replace(" ", "").replace(',', '.')
            val w = text.toDoubleOrNull() ?: return null
            return Parsed(w * s.multiplier, "kg", stableFlag(line))
        }

        // Prefer a number that has a unit after it; otherwise take the first number on the line.
        var chosen: MatchResult? = null
        for (m in numberWithUnit.findAll(line)) {
            if (m.groups[3] != null) { chosen = m; break }
            if (chosen == null) chosen = m
        }
        val m = chosen ?: return null
        val unit = m.groups[3]?.value?.lowercase()
        if (s.requireUnit && unit == null) return null

        val sign = if (m.groups[1]?.value == "-") -1.0 else 1.0
        val value = m.groups[2]?.value?.replace(',', '.')?.toDoubleOrNull() ?: return null
        var kg = sign * value * s.multiplier
        when (unit) {
            "g" -> kg /= 1000.0
            "lb" -> kg *= 0.45359237
            "oz" -> kg *= 0.028349523125
        }
        return Parsed(kg, "kg", stableFlag(line))
    }

    /** True/false if the indicator says so on the line, null if it doesn't (then we work it out from repeat readings). */
    private fun stableFlag(line: String): Boolean? {
        val u = line.uppercase()
        return when {
            unstableToken.containsMatchIn(u) -> false
            stableToken.containsMatchIn(u) -> true
            else -> null
        }
    }
}
