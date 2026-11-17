package my.klean.scalebridge

import android.content.Context
import com.hoho.android.usbserial.driver.UsbSerialPort

/** Everything the centre (or Klean support) can change on the tablet without rebuilding the app. */
data class Settings(
    val dashboardUrl: String = BuildConfig.DEFAULT_DASHBOARD_URL,
    val baudRate: Int = 9600,
    val dataBits: Int = 8,
    val parity: String = "none",          // none | even | odd
    val stopBits: Int = 1,                // 1 | 2
    val requireUnit: Boolean = true,      // ignore lines with no kg/g/lb (e.g. date/time lines in print mode)
    val stableCount: Int = 5,             // readings that must agree before the weight counts as stable
    val stableTolerance: Double = 0.02,   // how close (in kg) those readings must be
    val customRegex: String = "",         // optional: first capture group = the weight
    val multiplier: Double = 1.0          // e.g. 0.001 if the indicator sends grams with no unit
) {
    fun parityValue(): Int = when (parity) {
        "even" -> UsbSerialPort.PARITY_EVEN
        "odd" -> UsbSerialPort.PARITY_ODD
        else -> UsbSerialPort.PARITY_NONE
    }

    fun stopBitsValue(): Int = if (stopBits == 2) UsbSerialPort.STOPBITS_2 else UsbSerialPort.STOPBITS_1

    fun hasDashboard(): Boolean =
        dashboardUrl.startsWith("http") && !dashboardUrl.contains("YOUR-HERCULES")

    companion object {
        private const val PREFS = "scale_settings"

        fun load(context: Context): Settings {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val d = Settings()
            return Settings(
                dashboardUrl = p.getString("url", d.dashboardUrl) ?: d.dashboardUrl,
                baudRate = p.getInt("baud", d.baudRate),
                dataBits = p.getInt("dataBits", d.dataBits),
                parity = p.getString("parity", d.parity) ?: d.parity,
                stopBits = p.getInt("stopBits", d.stopBits),
                requireUnit = p.getBoolean("requireUnit", d.requireUnit),
                stableCount = p.getInt("stableCount", d.stableCount),
                stableTolerance = p.getFloat("stableTol", d.stableTolerance.toFloat()).toDouble(),
                customRegex = p.getString("regex", d.customRegex) ?: d.customRegex,
                multiplier = p.getFloat("mult", d.multiplier.toFloat()).toDouble()
            )
        }

        fun save(context: Context, s: Settings) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("url", s.dashboardUrl.trim())
                .putInt("baud", s.baudRate)
                .putInt("dataBits", s.dataBits)
                .putString("parity", s.parity)
                .putInt("stopBits", s.stopBits)
                .putBoolean("requireUnit", s.requireUnit)
                .putInt("stableCount", s.stableCount)
                .putFloat("stableTol", s.stableTolerance.toFloat())
                .putString("regex", s.customRegex)
                .putFloat("mult", s.multiplier.toFloat())
                .apply()
        }
    }
}
