CONTEXT
We are Klean (Janz Technologies Sdn Bhd). The Wira Kitar Recycler Dashboard records what collection centres buy from informal waste collectors. Collectors will onboard themselves on WhatsApp: read the collector notice in short parts, confirm their age, give consent, and receive a unique Wira Kitar QR ID that staff scan at the centre. Consent and QR records are anchored on the Hedera blockchain by our existing nightly anchoring job (no personal data on chain).

This REPLACES the earlier 360dialog and respond.io prompts. We connect directly to Meta's WhatsApp Business Cloud API (no middleman platform), and this app runs the whole conversation.

Build in stages, pausing after each part so I can test. Keep everything already built (scale capture, visits and bags, advances, cash float, anchoring, corrections, reports, ISO 59014 work) working exactly as it is. Collectors often have limited literacy: short messages, buttons instead of typing, optional voice notes, Bahasa Malaysia first.

SECRETS (server-side only, never in the client or logs)
- WA_ACCESS_TOKEN: permanent System User token from Meta Business Settings.
- WA_PHONE_NUMBER_ID: the WhatsApp phone number ID (Meta test number first, real number later).
- WA_APP_SECRET: the Meta app secret, for webhook signatures.
- WA_VERIFY_TOKEN: a random string I also type into the Meta webhook settings.
- QR_SIGNING_SECRET: random 32+ byte secret for QR signatures.
- PHONE_HASH_SALT: random salt for hashing phone numbers in logs.
- WA_TEST_MODE (true/false): when true, add "[UJIAN]" to the welcome text and allow only numbers in an admin allow-list.

PART 1. WHATSAPP CLOUD API CONNECTION
1. Webhook on https://ideal-bulldog-102.convex.site/api/whatsapp/webhook:
   - GET: Meta verification. If hub.mode = "subscribe" and hub.verify_token = WA_VERIFY_TOKEN, return hub.challenge as plain text; otherwise 403.
   - POST: verify the X-Hub-Signature-256 header (HMAC-SHA256 of the raw body with WA_APP_SECRET) before reading anything; 401 if invalid. Return 200 immediately and process asynchronously (Convex scheduler). Meta retries failed deliveries, so make processing idempotent on the WhatsApp message id.
   - Handle entry[].changes[].value.messages (text, interactive button_reply / list_reply, audio) and value.statuses (sent/delivered/read/failed, for the log).
2. Sending: POST https://graph.facebook.com/<current API version>/<WA_PHONE_NUMBER_ID>/messages with Bearer WA_ACCESS_TOKEN, messaging_product "whatsapp". Build helpers for: text, interactive reply buttons (max 3 buttons, titles max 20 characters), interactive list (for more than 3 options), image (upload media first via /<WA_PHONE_NUMBER_ID>/media and send by media id), audio, and mark-as-read.
3. 24-hour rule: free-form messages only within 24 hours of the collector's last message. Everything in this prompt is a reply to the collector, so it stays inside the window. If a send fails with the "outside window" error, log it and flag it in the admin inbox; do not retry.
4. whatsapp_message_log for every inbound and outbound message: id, direction, wa_message_id, phone_hash (SHA-256 of phone + PHONE_HASH_SALT), collector_id, session_id, type, button_id or command, status, created_at. Do not log message text beyond button ids, commands and the fields we collect.
5. Rate limit: max 20 inbound messages per number per hour; ignore and log the excess.

PART 2. ONBOARDING CONVERSATION
Start: any message from a number that is not a registered collector, or "DAFTAR" / "DAFTAR <centre code>" (centre posters carry a wa.me link with this text pre-filled, so we know the referring centre). Store progress in onboarding_session so a collector can stop and resume; sessions expire after 7 days.

Steps (text comes from the admin Notice manager, Part 6; seed it with notice v1.0 below):
1. Language. Buttons: [Bahasa Malaysia] [English]. Greeting text: "Selamat datang ke Wira Kitar! / Welcome to Wira Kitar! Pilih bahasa / Choose language:" Design so more languages (e.g. Bengali, Burmese) can be added from admin later; with more than 3 languages use a list.
2. Notice in 5 parts. Each part is one message with buttons [Faham / Understood] [Dengar / Listen] (sends the voice note for that part, if uploaded) [Tanya / Ask] (creates a question for KLEAN staff, Part 5). Only [Faham] moves to the next part. Record a timestamp per acknowledged part.
3. Age: "Adakah anda berumur 18 tahun ke atas?" / "Are you 18 years old or above?" [Ya] [Tidak] / [Yes] [No]. If no: reply "Terima kasih. Wira Kitar hanya untuk orang dewasa berumur 18 tahun ke atas, jadi kami tidak dapat mendaftarkan anda. Maklumat anda tidak disimpan. Jaga diri!" (EN: "Thank you. Wira Kitar is only for adults aged 18 and above, so we can't register you. Your information has not been kept. Take care!"), end the session, delete the session's personal data, keep only an anonymous count.
4. Consent: "Saya telah membaca/mendengar notis ini dan bersetuju menyertai Wira Kitar." / "I have read or heard this notice and agree to join Wira Kitar." [Setuju] [Tidak setuju] / [Agree] [Don't agree]. If not agreed: "Terima kasih kerana meluangkan masa. Anda tidak didaftarkan dan maklumat anda tidak disimpan. Jika anda berubah fikiran, hantar DAFTAR bila-bila masa." (EN: "Thank you for your time. You have not been registered and your information has not been kept. If you change your mind, send DAFTAR at any time."), end, delete session data, anonymous count only.
5. Optional: "Nama anda? (boleh langkau)" with [Langkau / Skip]. No photo on WhatsApp; staff may take one at first visit with consent.
6. Create or link the collector (Part 3), record consent, send the QR (Part 4).

Notice v1.0 text (seed data):
BM
(1/5) APA YANG ANDA DAPAT: • Bayaran penuh daripada pusat untuk setiap timbangan, pada harga yang dipaparkan, secara tunai atau Touch 'n Go, dengan resit. • Bahagian yuran Wira Kitar daripada KLEAN, sebagai tambahan kepada harga pusat. • Rekod pendapatan anda sendiri. • Latihan keselamatan dan peralatan perlindungan (sarung tangan, jaket) secara percuma.
(2/5) CARA TIMBANGAN DIBUAT: • Setiap beg ditimbang di atas penimbang yang disambung terus ke sistem. Anda boleh melihat nombor pada skrin. • Jika anda tidak setuju dengan berat, minta timbang semula di depan anda.
(3/5) MAKLUMAT YANG KAMI SIMPAN: • Nombor telefon, nombor kenderaan (jika ada), nama (jika anda beri), kawasan barang dikutip, dan rekod timbangan serta bayaran. • Kami guna maklumat ini hanya untuk membayar anda, merekod kitar semula, dan laporan kepada syarikat jenama tanpa nama anda. • Nama, telefon dan bayaran anda tidak diterbitkan dan tidak dimasukkan ke dalam blockchain. • Anda boleh minta lihat atau betulkan maklumat anda pada bila-bila masa.
(4/5) JANJI ANDA: • Umur anda 18 tahun ke atas. • Anda hanya menjual barang yang diperoleh secara sah. Tiada barang curi seperti penutup longkang atau kabel. • Anda tidak cuba menipu timbangan.
(5/5) HAK ANDA: • Anda bebas menjual di tempat lain dan boleh berhenti daripada Wira Kitar pada bila-bila masa. • Pendahuluan (pinjaman) adalah pilihan anda, dengan syarat bertulis. Tiada sesiapa boleh menyimpan IC, telefon atau dokumen kenderaan anda. • Anda boleh membuat aduan tanpa nama melalui kod QR atau WhatsApp. Kami jawab dalam 14 hari. Anda tidak akan dihukum kerana mengadu.
EN
(1/5) WHAT YOU GET: • Full payment from the centre for every weighing, at the posted price, in cash or by Touch 'n Go, with a receipt. • A Wira Kitar fee share from KLEAN, on top of the centre's price. • Your own income record. • Free safety training and protective equipment (gloves, vest).
(2/5) HOW WEIGHING WORKS: • Every bag is weighed on a scale connected directly to the system. You can see the number on the screen. • If you disagree with a weight, ask for it to be reweighed in front of you.
(3/5) WHAT WE KEEP: • Your phone number, vehicle plate (if any), name (if you give it), the area your material came from, and your weighing and payment records. • We use this only to pay you, record recycling, and report to brand companies without your name. • Your name, phone number and payments are never published and never put on the blockchain. • You can ask to see or correct your information at any time.
(4/5) YOUR PROMISES: • You are 18 or older. • You only sell material you obtained lawfully. No stolen items such as manhole covers or cable. • You won't try to cheat the scale.
(5/5) YOUR RIGHTS: • You are free to sell elsewhere and can leave Wira Kitar at any time. • Advances (pinjaman) are your choice, with written terms. No one may keep your IC, phone or vehicle documents. • You can complain anonymously by QR code or WhatsApp. We reply within 14 days. You won't be punished for complaining.

PART 3. CREATE OR LINK THE COLLECTOR, RECORD CONSENT
1. If a collector with this phone already exists (e.g. registered at the counter), link to them and keep their existing ID. Otherwise create one.
2. Collector ID: use the EXISTING anonymous format COL-0001, COL-0002, ... from the same sequence the dashboard already uses. Never reuse a number, even after withdrawal. Existing collectors keep their COL id (it is already in anchored blockchain records).
3. Collector record: collector_ref, phone (the WhatsApp number, verified by WhatsApp), name if given, referring_centre, language ("ms" / "en"), onboarded_via = "whatsapp", status = active.
4. Immutable collector_consent: collector_id, notice_version, language, consent = true, age_confirmed = true, method = "whatsapp", parts_acknowledged (count) with a timestamp per part, wa message ids, created_at (ISO UTC), salt (32 random hex characters). Withdrawal creates a new record with consent = false; never edit this one.

PART 4. WIRA KITAR QR ID AND COMMANDS
1. QR payload: "WK1:" + collector_ref + ":" + issue_number + ":" + sig, where sig = first 16 hex characters of HMAC-SHA256(QR_SIGNING_SECRET, collector_ref + ":" + issue_number). Example: WK1:COL-0042:1:3f9a0c1b7d2e4a65. No phone, name or plate in the QR.
2. PNG with high error correction, "WIRA KITAR" and the collector_ref printed under it, colours #066839 and #1FA63D. Upload to WhatsApp media and send as an image, followed by:
   BM: "Ini ID Wira Kitar anda: COL-0042. Tunjukkan kod QR ini di pusat kitar semula setiap kali anda menimbang. Hantar KOD untuk dapatkan semula kod, HILANG jika telefon hilang, REKOD untuk rekod bulan ini, ADUAN untuk membuat aduan, BERHENTI untuk berhenti, BANTUAN untuk senarai arahan."
   EN: "Your Wira Kitar ID is COL-0042. Show this QR code at the recycling centre every time you weigh. Send KOD to get your code again, HILANG if you lose your phone, REKOD for this month's record, ADUAN to make a complaint, BERHENTI to leave, BANTUAN for the list of commands."
3. Printable credit-card size card from the collector profile (QR, collector_ref, first name if given) for collectors without smartphones.
4. qr_issue: collector_id, issue_number, issued_at, revoked_at, reason, plus anchor fields.
5. Commands from registered collectors (case-insensitive, first word):
   - KOD: resend current QR.
   - HILANG: revoke current QR (reason "lost"), issue issue_number + 1, send it. Old QR stops working immediately.
   - REKOD: this month's total kg, number of visits and Wira Kitar fee share, this collector only.
   - ADUAN: create a grievance (same module as the ISO 59014 work); category list, then free text or voice note.
   - BERHENTI: confirm with [Ya, berhenti] [Batal]. On confirm: status = withdrawn, revoke QR, new consent record with consent = false, goodbye message, stop messaging. Past weighing records kept anonymised as the notice says.
   - BANTUAN: list commands.
   - Anything else: Part 5.

PART 5. QUESTIONS AND HUMAN HELP
- [Tanya] and any message the bot doesn't understand create a "Soalan" item in an admin inbox showing the conversation. Staff reply from the dashboard as WhatsApp text, within the 24-hour window (show a countdown; after it closes, staff can only send an approved template, which I will create in Meta later).
- Flag as urgent and notify admins immediately if a message mentions danger, violence, a child, or police.

PART 6. ADMIN
- WhatsApp settings page: connection status (last webhook received), test-mode switch and allow-list, display of the phone number in use. Secrets are set as environment variables, never shown.
- Notice manager: versions of the 5 parts per language, optional voice note per part, effective date. Publishing a new version means existing collectors re-consent: next time they message or are scanned, walk them through the changed parts and ask for Setuju again.
- Onboarding funnel by week and referring centre: started, each part completed, age No, consent No, completed, average time to complete.
- Collector list: onboarded_via, notice version consented, QR issue number, status.
- Poster generator per centre: A4 PDF with a wa.me QR pre-filling "DAFTAR <centre code>", instructions in BM and EN.

PART 7. SCAN AT THE CENTRE
1. New Visit: large "Imbas QR" button first (keep plate/phone search as backup). Device camera (Android app and Chrome on a laptop webcam), reliable JS QR library.
2. On scan: check prefix, recompute signature, check issue_number is current and collector active. Valid: confirmation card with collector_ref, first name, photo if any (else initials), referring centre, last visit date, advance owed. [Betul / Correct] starts the visit; [Bukan orang ini / Not this person] logs possible misuse and returns to search.
3. Invalid, revoked or withdrawn: clear red message in BM, e.g. "Kod lama. Minta pengumpul hantar KOD di WhatsApp."
4. Store on each visit: identified_by = "qr" | "search" | "new", qr_issue_number.
5. First visit after WhatsApp onboarding: prompt staff to offer PPE and, with agreement, take a profile photo.

PART 8. BLOCKCHAIN ANCHORING (consent and QR records)
Add to the nightly anchoring export as new record types, alongside bags, exactly these fields and no personal data (the anchoring job already accepts them):
- record_type "consent": record_type, record_id, collector_ref, notice_version, language, consent (true/false), age_confirmed (true/false), method, parts_acknowledged (number), created_at (ISO UTC), salt.
- record_type "qr_issue": record_type, record_id, collector_ref, issue_number, event ("issued" | "revoked"), created_at (ISO UTC), salt.
These belong to the central registry, not a centre: list them in GET /api/anchoring/pending and export under centre code "WK" (e.g. /api/anchoring/export?centre=WK&date=2026-11-25), one batch per day. Same import endpoint and locking rule. On the collector profile show "Persetujuan direkod di blockchain" with the anchor date and a Download proof button.

DATA MODEL (add or adjust)
- onboarding_session: id, phone, language, step, parts_acknowledged (with timestamps), referring_centre, started_at, updated_at, completed_at, outcome (completed | under_18 | declined | expired).
- collector: collector_ref (COL-xxxx, existing sequence), onboarded_via, language, status (active | suspended | withdrawn), referring_centre.
- collector_consent, qr_issue: as above, plus anchor fields.
- notice_version: version, language, part_no, text, audio_file, effective_from.
- whatsapp_message_log: as Part 1.
- visit: identified_by, qr_issue_number.

PRIVACY AND SAFETY
- Never put phone numbers, names or plates in the QR, the anchoring export or the blockchain.
- Phone numbers in logs are salted hashes; the plain number is only on the collector record.
- Under-18 and declined sessions: delete personal data within 24 hours, keep only anonymous counts.

DONE WHEN
- From an allow-listed phone I message the Meta test number "DAFTAR C003", go through all 5 parts with buttons, confirm age and consent, and receive my QR image and welcome text with a COL id within 1 minute.
- An existing counter-registered collector who does the same keeps their old COL id.
- Staff scan the QR on the tablet and the visit starts after one confirmation tap.
- HILANG makes the old QR fail and sends a new one; BERHENTI withdraws the collector.
- Under-18 and declined attempts leave no personal data.
- The next nightly anchoring run includes the consent and qr_issue records under "WK" and they show as anchored the next day.
