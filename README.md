# Wira Kitar Scale (Android)

A small Android app for the recycling centre's touch screen. It does two things:

1. Opens the **Wira Kitar Recycler Dashboard (Hercules)** full screen.
2. Reads the weight from the scale's **3SM M-15 weighing indicator** over RS232 and passes it live to the dashboard.

The dashboard does everything else: collectors, materials, prices, payments, saving and uploading. The app never stores or sends transactions itself, so screen changes in Hercules appear on the tablet straight away, with no app update.

```
Scale -> 3SM M-15 indicator -> RS232 cable -> USB-RS232 adapter -> OTG -> Android tablet
                                                       app reads weight -> Hercules dashboard (web)
```

## What you need

| Item | Notes |
| --- | --- |
| Android tablet, 10" or larger, Android 7 or newer | One that supports USB OTG (almost all do). A rugged case is worth it. |
| USB-to-RS232 (DB9) adapter | Choose an **FTDI** chip if possible. Prolific PL2303, CH340 and CP210x also work. |
| USB-C (or micro-USB) OTG adapter | Better: a powered OTG hub, so the tablet charges while the scale is plugged in. |
| Null-modem adapter (DB9) | Only if no data arrives with the straight cable. It's cheap, so buy one with the adapter. |

## Build and install (one-off, for your developer)

1. Install **Android Studio** (free).
2. *File > Open* this folder (`KleanScaleBridge`). Let Gradle sync; it downloads what it needs.
3. In `app/build.gradle.kts`, set `DEFAULT_DASHBOARD_URL` to your Hercules dashboard address. You can also leave it and type the address on the tablet later.
4. On the tablet, turn on Developer options and USB debugging, connect it to the computer, and press **Run**.
   Or use *Build > Build APK(s)*, copy `app/build/outputs/apk/debug/app-debug.apk` to the tablet and open it to install.

## First run at the centre

1. Plug the indicator's RS232 port into the USB adapter, then into the tablet through the OTG adapter or hub.
2. Open **Wira Kitar Scale**. When Android asks to allow access to the USB device, tick "Always" and tap **OK**.
3. If no dashboard address is set yet, the **scale test page** opens. Put something on the scale:
   - The big number should match the indicator's display.
   - The black box shows the exact text the indicator sends.
4. Hold the faint **gear icon** (bottom right) to open Settings. Enter the dashboard address and tap **Save and reconnect scale**.

## The 3SM M-15 indicator

- In the indicator's setup menu, set the serial output to **continuous** if it has that option. "Print" mode only sends a weight when the print key is pressed.
- The usual settings are **9600 baud, 8 data bits, no parity, 1 stop bit**. The app's settings must match the indicator.

## Troubleshooting

| Problem | Fix |
| --- | --- |
| "No USB serial adapter found" | Check the OTG adapter. Try another cable. Some cheap OTG adapters are charge-only. |
| Connected, but nothing in the black box | Wrong baud rate (try 2400, 4800, 9600, 19200), or the cable needs a null-modem adapter, or the indicator is in print mode. |
| Lines appear in yellow but no weight | The format isn't recognised. Screenshot the yellow lines and send them to Klean. A custom pattern in Settings will fix it. |
| Weight never shows "Stable" | Increase *Stable tolerance* (for example to 0.05) or reduce *Readings that must agree* (for example to 3). |
| Grams shown as kg | Set *multiply weight by* to 0.001. |

## For developers: what the page receives

```js
// Every reading (up to 10 a second)
window.addEventListener('klean-scale', e => {
  // e.detail = { weight: 12.34, parsed: true, unit: 'kg', stable: true,
  //              raw: 'ST,GS,+0012.34kg', ts: 1731840000000, source: 'scale' }
});

// Connected / disconnected
window.addEventListener('klean-scale-status', e => {
  // e.detail = { connected: true, message: 'Scale connected: ...', device: '...', baud: 9600 }
});

// Lines that arrived but weren't recognised as a weight
window.addEventListener('klean-scale-raw', e => console.log(e.detail.raw));

// Only exists inside the app
if (window.KleanScale) {
  const status = JSON.parse(KleanScale.getStatus());
  const latest = JSON.parse(KleanScale.getLatest()); // may be null
  // KleanScale.reconnect(), KleanScale.openSettings(), KleanScale.getVersion()
}
```

See `HERCULES_PROMPT.md` for the prompt that adds this to the Hercules dashboard.

## Project layout

```
app/src/main/java/my/klean/scalebridge/
  MainActivity.kt     full-screen dashboard, passes weights to the page, camera/photo upload
  ScaleReader.kt      USB serial connection, auto-reconnect, stability check
  WeightParser.kt     turns indicator text into kg (handles ST/US, GS/NT, kg/g/lb)
  Settings.kt         settings saved on the tablet
  SettingsActivity.kt settings screen (hold the gear icon)
app/src/main/assets/scale-test.html   built-in scale test page
```

Uses the open-source **usb-serial-for-android** library (MIT licence).
