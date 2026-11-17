# Prompt for Hercules: connect the recycler dashboard to the scale

Paste everything between the lines into Hercules. Fill in the parts in [square brackets] first.

---

Update the Wira Kitar Recycler Dashboard so it can take weights directly from a weighing scale when it is opened on our Android tablet.

HOW THE SCALE WORKS
The dashboard will run inside our Android app, "Wira Kitar Scale". The app reads the weighing indicator through a USB cable and passes every reading to the web page. Nothing needs installing on the server for this. The page only needs to listen in the browser:

- `window.addEventListener('klean-scale', e => { ... })`. This fires up to 10 times a second. `e.detail` looks like:
  `{ "weight": 12.34, "parsed": true, "unit": "kg", "stable": true, "raw": "ST,GS,+0012.34kg", "ts": 1731840000000, "source": "scale" }`
  - `weight` is always in kg, to 3 decimals.
  - `stable` is true when the scale has settled.
  - `raw` is the exact line the indicator sent. Keep it as proof.
- `window.addEventListener('klean-scale-status', e => { ... })`. This fires when the scale connects or disconnects. `e.detail` looks like:
  `{ "connected": true, "message": "Scale connected: FT232R (FtdiSerialDriver) at 9600 baud", "device": "...", "baud": 9600 }`
- `window.KleanScale` only exists inside the app. On page load, if it exists, call `JSON.parse(window.KleanScale.getStatus())` and `JSON.parse(window.KleanScale.getLatest())` to get the current state, because events may have fired before the page was ready. `getLatest()` can return `null`.
  - Other functions you can call: `window.KleanScale.reconnect()`, `window.KleanScale.openSettings()` and `window.KleanScale.getVersion()`.
- Wrap all of this in one small reusable hook or component, for example `useScale()`, returning `{ available, connected, statusMessage, weight, stable, raw }`. `available` is false when `window.KleanScale` is undefined, which means a normal laptop browser.

CHANGES TO THE NEW PURCHASE SCREEN
1. Add a large live weight panel at the top of the line-item area:
   - The current weight in very large digits, at least 72px, in kg to 2 decimals.
   - A status pill: green "Stable" when `stable` is true, amber "Settling…" when false, and red "Scale not connected" when disconnected. Include a "Reconnect" button that calls `window.KleanScale.reconnect()`.
2. Make material selection a grid of large touch buttons, at least 96px tall, one per active material type (PET bottles, HDPE, aluminium cans, UBC cartons, cardboard and so on), each showing the material name and its current price per kg.
3. Add a large "Capture weight" button:
   - It is enabled only when a material is selected, the scale is connected, the reading is stable and the weight is above [0.05] kg.
   - Pressing it adds a line item with that material and the captured weight, and calculates the line amount from the price per kg.
   - Store on the line item: `weight_kg`, `weight_source = 'scale'`, `scale_raw` (the raw text), `scale_stable = true` and `captured_at`.
   - Captured weights are read-only. Staff cannot type over them.
   - To correct a mistake, staff delete the line and capture again.
4. Manual weight entry:
   - When the page is not inside the app (laptop) or the scale is disconnected, show a "Enter weight manually" option.
   - Manual lines save `weight_source = 'manual'` and require a reason chosen from a dropdown: Scale broken, Item too large for scale, or Other (with text).
   - Every manual line is automatically flagged for Klean admin review and shown with a "Manual" badge everywhere it appears.
5. Show a running total of kg by material and the total amount payable before the centre confirms payment.
6. Touch-friendly layout for a 10-inch tablet in landscape: large text, large buttons, no hover-only controls, and nothing that needs a right-click.

DATA AND REPORTING
- Add `weight_source`, `scale_raw`, `scale_stable` and `captured_at` to the transaction line items table.
- In the Klean admin view, add a filter for "Manual weights" and show the share of kg captured by scale versus manual entry for each centre.
- In the producer EPR report, include only scale-captured weights by default, with a toggle to include approved manual weights.

TESTING WITHOUT THE TABLET
Add a hidden developer option, shown only when the URL contains `?scaleSim=1`. It shows a small slider (0 to 50 kg) and a "stable" checkbox that fire the same `klean-scale` events, so the screens can be tested in a normal browser.

Keep everything else in the dashboard as it is.

---
