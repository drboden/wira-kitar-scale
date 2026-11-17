package my.klean.scalebridge

import android.annotation.SuppressLint
import android.content.Intent
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.view.Gravity
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.json.JSONObject
import java.io.File

/**
 * Full-screen shell around the Hercules recycler dashboard.
 * The only native job is reading the scale and handing each weight to the web page:
 *   window.addEventListener('klean-scale', e => e.detail)          // every parsed reading
 *   window.addEventListener('klean-scale-status', e => e.detail)   // connected / disconnected
 *   window.KleanScale.getLatest()                                  // latest reading as JSON text
 * All saving and uploading is done by the dashboard itself.
 */
class MainActivity : AppCompatActivity(), ScaleReader.Listener {

    private lateinit var webView: WebView
    private lateinit var scale: ScaleReader
    private var loadedUrl: String? = null
    private var activeSettings: Settings? = null
    private var lastPush = 0L
    private var lastStable: Boolean? = null

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraUri: Uri? = null

    private val pickFile =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = filePathCallback ?: return@registerForActivityResult
            filePathCallback = null
            var uris: Array<Uri>? = null
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val clip = data?.clipData
                uris = when {
                    clip != null -> Array(clip.itemCount) { clip.getItemAt(it).uri }
                    data?.data != null -> arrayOf(data.data!!)
                    cameraUri != null -> arrayOf(cameraUri!!)
                    else -> null
                }
            }
            callback.onReceiveValue(uris)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = FrameLayout(this)
        webView = WebView(this)
        root.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // Small, faint gear in the corner. Hold it to open scale settings (a tap only shows a hint).
        val gear = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_manage)
            background = null
            alpha = 0.3f
            contentDescription = "Scale settings"
            setOnClickListener {
                Toast.makeText(this@MainActivity, "Hold to open scale settings", Toast.LENGTH_SHORT).show()
            }
            setOnLongClickListener { openSettings(); true }
        }
        root.addView(gear, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.BOTTOM or Gravity.END))
        setContentView(root)

        setupWebView()
        scale = ScaleReader(this, this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack()
                // Otherwise do nothing, so staff can't close the app by accident.
            }
        })

        loadDashboard()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        val s = Settings.load(this)
        if (intent?.getBooleanExtra(EXTRA_OPEN_TEST, false) == true) {
            intent.removeExtra(EXTRA_OPEN_TEST)
            loadTestPage()
        } else if (targetUrl(s) != loadedUrl) {
            loadDashboard()
        }
        // Only (re)connect when settings changed or the scale isn't connected,
        // so returning from the camera doesn't interrupt a weighing.
        if (s != activeSettings || !scale.connected) {
            activeSettings = s
            scale.start(s)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            scale.start(Settings.load(this))
        }
    }

    override fun onDestroy() {
        scale.release()
        webView.destroy()
        super.onDestroy()
    }

    // ---- web page ----

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            userAgentString = "$userAgentString KleanScaleBridge/${BuildConfig.VERSION_NAME}"
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.addJavascriptInterface(Bridge(), "KleanScale")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // Tell the freshly loaded page where the scale stands.
                pushStatus()
                scale.latest?.let { pushReading(it) }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                launchChooser(params)
                return true
            }
        }
    }

    private fun targetUrl(s: Settings): String =
        if (s.hasDashboard()) s.dashboardUrl.trim() else TEST_PAGE

    private fun loadDashboard() {
        val url = targetUrl(Settings.load(this))
        loadedUrl = url
        webView.loadUrl(url)
    }

    private fun loadTestPage() {
        loadedUrl = TEST_PAGE
        webView.loadUrl(TEST_PAGE)
    }

    /** Lets the dashboard's "add photo" button use either the camera or the gallery. */
    private fun launchChooser(params: WebChromeClient.FileChooserParams?) {
        val content = params?.createIntent()
            ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
        val accept = params?.acceptTypes?.toList().orEmpty()
        val wantsImage = accept.isEmpty() || accept.any { it.isBlank() || it.startsWith("image") }

        val extra = mutableListOf<Intent>()
        cameraUri = null
        if (wantsImage) {
            try {
                val dir = File(cacheDir, "camera").apply { mkdirs() }
                val file = File.createTempFile("load_", ".jpg", dir)
                val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
                cameraUri = uri
                extra += Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    .putExtra(MediaStore.EXTRA_OUTPUT, uri)
                    .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } catch (_: Exception) { }
        }
        val chooser = Intent.createChooser(content, "Add photo")
            .putExtra(Intent.EXTRA_INITIAL_INTENTS, extra.toTypedArray())
        try {
            pickFile.launch(chooser)
        } catch (_: Exception) {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
        }
    }

    // ---- scale -> page ----

    override fun onReading(reading: Reading) {
        if (!reading.parsed) {
            dispatch("klean-scale-raw", reading.toJson())
            return
        }
        // Up to ~10 updates a second, but never miss a change between moving and stable.
        val now = SystemClock.uptimeMillis()
        val stableChanged = lastStable != reading.stable
        if (!stableChanged && now - lastPush < 100) return
        lastPush = now
        lastStable = reading.stable
        pushReading(reading)
    }

    override fun onStatus(connected: Boolean, message: String) {
        pushStatus()
    }

    private fun pushReading(reading: Reading) = dispatch("klean-scale", reading.toJson())

    private fun pushStatus() = dispatch("klean-scale-status", scale.statusJson())

    private fun dispatch(event: String, detail: JSONObject) {
        val js = "window.dispatchEvent(new CustomEvent('$event',{detail:$detail}));"
        webView.evaluateJavascript(js, null)
    }

    // ---- what the dashboard can call: window.KleanScale.* ----

    inner class Bridge {
        @JavascriptInterface
        fun getLatest(): String = scale.latest?.toJson()?.toString() ?: "null"

        @JavascriptInterface
        fun isConnected(): Boolean = scale.connected

        @JavascriptInterface
        fun getStatus(): String = scale.statusJson().toString()

        @JavascriptInterface
        fun getVersion(): String = BuildConfig.VERSION_NAME

        @JavascriptInterface
        fun reconnect() {
            runOnUiThread { scale.start(Settings.load(this@MainActivity)) }
        }

        @JavascriptInterface
        fun openSettings() {
            runOnUiThread { this@MainActivity.openSettings() }
        }

        @JavascriptInterface
        fun reloadDashboard() {
            runOnUiThread { loadDashboard() }
        }
    }

    // ---- helpers ----

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_OPEN_TEST = "open_test"
        const val TEST_PAGE = "file:///android_asset/scale-test.html"
    }
}
