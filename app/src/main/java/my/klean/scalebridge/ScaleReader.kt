package my.klean.scalebridge

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import org.json.JSONObject

/**
 * Reads the weighing indicator through a USB-to-RS232 adapter and reports each weight.
 * Reconnects by itself if the cable is pulled out and plugged back in.
 */
class ScaleReader(
    private val context: Context,
    private val listener: Listener
) : SerialInputOutputManager.Listener {

    interface Listener {
        fun onReading(reading: Reading)
        fun onStatus(connected: Boolean, message: String)
    }

    @Volatile var latest: Reading? = null
        private set
    @Volatile var connected = false
        private set
    @Volatile var statusMessage = "Not started"
        private set
    @Volatile var deviceName = ""
        private set

    private val main = Handler(Looper.getMainLooper())
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val actionPermission = context.packageName + ".USB_PERMISSION"

    private var settings = Settings()
    private var port: UsbSerialPort? = null
    private var io: SerialInputOutputManager? = null
    private var receiverRegistered = false

    private val buffer = StringBuilder()
    private val recent = ArrayDeque<Double>()

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            if (intent.action != actionPermission) return
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                open()
            } else {
                setStatus(false, "USB permission refused. Unplug the adapter, plug it back in and tap OK.")
            }
        }
    }

    fun start(newSettings: Settings) {
        stop()
        settings = newSettings
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                context, permissionReceiver, IntentFilter(actionPermission),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        }
        open()
    }

    fun stop() {
        main.removeCallbacksAndMessages(null)
        closePort()
        connected = false
    }

    fun release() {
        stop()
        if (receiverRegistered) {
            try { context.unregisterReceiver(permissionReceiver) } catch (_: Exception) { }
            receiverRegistered = false
        }
    }

    fun statusJson(): JSONObject = JSONObject()
        .put("connected", connected)
        .put("message", statusMessage)
        .put("device", deviceName)
        .put("baud", settings.baudRate)

    private fun open() {
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (drivers.isEmpty()) {
            setStatus(false, "No USB serial adapter found. Check the cable and OTG adapter.")
            scheduleRetry()
            return
        }
        val driver = drivers[0]
        val device = driver.device
        deviceName = (device.productName ?: "USB serial adapter") + " (" + driver.javaClass.simpleName + ")"

        if (!usbManager.hasPermission(device)) {
            val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val intent = Intent(actionPermission).setPackage(context.packageName)
            usbManager.requestPermission(device, PendingIntent.getBroadcast(context, 0, intent, flags))
            setStatus(false, "Waiting for USB permission. Tap OK on the tablet.")
            return
        }

        val connection = usbManager.openDevice(device)
        if (connection == null) {
            setStatus(false, "Could not open the USB adapter.")
            scheduleRetry()
            return
        }

        try {
            val p = driver.ports[0]
            p.open(connection)
            p.setParameters(settings.baudRate, settings.dataBits, settings.stopBitsValue(), settings.parityValue())
            try { p.setDTR(true); p.setRTS(true) } catch (_: Exception) { }
            port = p
            synchronized(buffer) { buffer.setLength(0) }
            recent.clear()
            io = SerialInputOutputManager(p, this).also { it.start() }
            setStatus(true, "Scale connected: $deviceName at ${settings.baudRate} baud")
        } catch (e: Exception) {
            setStatus(false, "Could not start the scale: ${e.message}")
            closePort()
            scheduleRetry()
        }
    }

    private fun closePort() {
        try { io?.stop() } catch (_: Exception) { }
        io = null
        try { port?.close() } catch (_: Exception) { }
        port = null
    }

    private fun scheduleRetry() {
        main.postDelayed({ if (!connected) open() }, 3000)
    }

    private fun setStatus(isConnected: Boolean, message: String) {
        connected = isConnected
        statusMessage = message
        main.post { listener.onStatus(isConnected, message) }
    }

    // ---- data from the indicator (runs on a background thread) ----

    override fun onNewData(data: ByteArray) {
        val text = String(data, Charsets.US_ASCII)
        val lines = mutableListOf<String>()
        synchronized(buffer) {
            buffer.append(text)
            while (true) {
                val idx = buffer.indexOfFirst { it == '\r' || it == '\n' || it == '\u0002' || it == '\u0003' }
                if (idx < 0) break
                val line = buffer.substring(0, idx).trim()
                buffer.delete(0, idx + 1)
                if (line.isNotEmpty()) lines.add(line)
            }
            if (buffer.length > 512) buffer.setLength(0) // junk at the wrong baud rate: start again
        }
        lines.forEach { handleLine(it) }
    }

    override fun onRunError(e: Exception) {
        main.post {
            closePort()
            setStatus(false, "Scale disconnected: ${e.message ?: "adapter unplugged"}")
            scheduleRetry()
        }
    }

    private fun handleLine(line: String) {
        val parsed = WeightParser.parse(line, settings)
        if (parsed == null) {
            // Still show unparsed lines on the test page so the format can be worked out.
            val raw = Reading(Double.NaN, "", false, line, System.currentTimeMillis())
            main.post { listener.onReading(raw) }
            return
        }
        recent.addLast(parsed.weight)
        while (recent.size > settings.stableCount) recent.removeFirst()
        val computedStable = recent.size >= settings.stableCount &&
            (recent.max() - recent.min()) <= settings.stableTolerance
        val reading = Reading(
            weight = parsed.weight,
            unit = parsed.unit,
            stable = parsed.stableFlag ?: computedStable,
            raw = line,
            timestamp = System.currentTimeMillis()
        )
        latest = reading
        main.post { listener.onReading(reading) }
    }
}
