package my.klean.scalebridge

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** Simple settings screen, opened by holding the gear icon. Built in code to keep the project small. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var url: EditText
    private lateinit var baud: Spinner
    private lateinit var parity: Spinner
    private lateinit var stopBits: Spinner
    private lateinit var requireUnit: CheckBox
    private lateinit var stableCount: EditText
    private lateinit var stableTol: EditText
    private lateinit var regex: EditText
    private lateinit var multiplier: EditText

    private val baudRates = listOf("1200", "2400", "4800", "9600", "19200", "38400", "57600", "115200")
    private val parities = listOf("none", "even", "odd")
    private val stops = listOf("1", "2")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val s = Settings.load(this)
        val pad = dp(24)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        col.addView(title("Wira Kitar scale settings"))
        col.addView(note("App version ${BuildConfig.VERSION_NAME}"))

        col.addView(label("Dashboard address (your Hercules recycler dashboard)"))
        url = EditText(this).apply {
            setText(s.dashboardUrl)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }
        col.addView(url)

        col.addView(label("Baud rate (must match the indicator; 9600 is the usual default)"))
        baud = spinner(baudRates, s.baudRate.toString())
        col.addView(baud)

        col.addView(label("Parity"))
        parity = spinner(parities, s.parity)
        col.addView(parity)

        col.addView(label("Stop bits"))
        stopBits = spinner(stops, s.stopBits.toString())
        col.addView(stopBits)

        requireUnit = CheckBox(this).apply {
            text = "Only accept lines that include a unit (kg, g, lb)"
            isChecked = s.requireUnit
        }
        col.addView(requireUnit)

        col.addView(label("Readings that must agree before the weight counts as stable"))
        stableCount = number(s.stableCount.toString(), decimal = false)
        col.addView(stableCount)

        col.addView(label("Stable tolerance (kg)"))
        stableTol = number(s.stableTolerance.toString(), decimal = true)
        col.addView(stableTol)

        col.addView(label("Advanced: custom pattern (leave empty unless Klean support asks)"))
        regex = EditText(this).apply { setText(s.customRegex); setSingleLine(true) }
        col.addView(regex)

        col.addView(label("Advanced: multiply weight by (1 = no change)"))
        multiplier = number(s.multiplier.toString(), decimal = true)
        col.addView(multiplier)

        col.addView(button("Save and reconnect scale") {
            save(); finish()
        })
        col.addView(button("Save and open scale test page") {
            save()
            startActivity(Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_TEST, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
        })
        col.addView(button("Cancel") { finish() })

        setContentView(ScrollView(this).apply { addView(col) })
    }

    private fun save() {
        val old = Settings.load(this)
        val s = old.copy(
            dashboardUrl = url.text.toString().trim(),
            baudRate = baud.selectedItem.toString().toInt(),
            parity = parity.selectedItem.toString(),
            stopBits = stopBits.selectedItem.toString().toInt(),
            requireUnit = requireUnit.isChecked,
            stableCount = stableCount.text.toString().toIntOrNull()?.coerceIn(1, 50) ?: old.stableCount,
            stableTolerance = stableTol.text.toString().toDoubleOrNull()?.coerceAtLeast(0.0) ?: old.stableTolerance,
            customRegex = regex.text.toString().trim(),
            multiplier = multiplier.text.toString().toDoubleOrNull()?.takeIf { it > 0 } ?: old.multiplier
        )
        Settings.save(this, s)
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
    }

    // ---- small UI helpers ----

    private fun title(t: String) = TextView(this).apply { text = t; textSize = 24f; setPadding(0, 0, 0, dp(8)) }
    private fun note(t: String) = TextView(this).apply { text = t; textSize = 13f; alpha = 0.6f }
    private fun label(t: String) = TextView(this).apply { text = t; textSize = 15f; setPadding(0, dp(18), 0, dp(4)) }

    private fun number(value: String, decimal: Boolean) = EditText(this).apply {
        setText(value)
        inputType = InputType.TYPE_CLASS_NUMBER or (if (decimal) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0)
        setSingleLine(true)
    }

    private fun spinner(items: List<String>, selected: String) = Spinner(this).apply {
        adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, items)
        setSelection(items.indexOf(selected).coerceAtLeast(0))
    }

    private fun button(t: String, action: () -> Unit) = Button(this).apply {
        text = t
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(16) }
        setOnClickListener { action() }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
