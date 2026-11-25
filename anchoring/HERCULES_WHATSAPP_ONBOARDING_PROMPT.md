CONTEXT
We are Klean (Janz Technologies Sdn Bhd). The Wira Kitar Recycler Dashboard records what collection centres buy from informal waste collectors. Today centre staff register collectors at the counter. We now want collectors to onboard themselves on WhatsApp: read the collector notice in short parts, confirm their age, give consent, and receive a unique Wira Kitar QR ID. At the centre, staff scan the QR to start a visit. Consent and ID records are anchored on the Hedera blockchain by our existing nightly anchoring job (no personal data on chain).

Build this in stages, pausing after each so I can test. Keep everything already built working as it is. Many collectors have limited literacy: short messages, buttons instead of typing, voice notes, Bahasa Malaysia first.

WHATSAPP PROVIDER
- Use the WhatsApp Business Cloud API through 360dialog (I will provide the API key and phone number; store them as server secrets, never in the client).
- Receive messages on a webhook endpoint in this app (POST /api/whatsapp/webhook), verify the provider's signature, and reply within the 24-hour customer service window using interactive reply buttons and lists.
- Log every inbound and outbound message (message id, time, collector/session id, type, button pressed) for audit. Do not log message text beyond button ids and the fields we collect.

PART 1. ONBOARDING CONVERSATION
Trigger: an inbound message containing "DAFTAR" (or any first message from an unknown number) starts onboarding. Centre posters carry a wa.me link with "DAFTAR <centre code>" pre-filled, so we know which centre referred them.

Steps (store progress in an onboarding_session so a collector can stop and resume later; sessions expire after 7 days):
1. Language: buttons [Bahasa Malaysia] [English]. (Design so more languages can be added from the admin screen.)
2. Notice in 5 parts, from the admin-managed notice text (current version). Each part is one short message plus an optional voice note (admin uploads an audio file per part per language), with buttons [Faham / Understood] and [Dengar / Listen] (sends the voice note) and [Tanya / Ask] (creates a question for KLEAN staff, see Part 5):
   - Part 1: What you get
   - Part 2: How weighing works
   - Part 3: What information we keep
   - Part 4: Your promises
   - Part 5: Your rights and how to complain
3. Age: "Adakah anda berumur 18 tahun ke atas?" [Ya] [Tidak]. If Tidak: reply kindly that Wira Kitar is for adults, end the session, delete the session's personal data, keep only an anonymous count. Do not create a collector.
4. Consent: "Saya telah membaca/mendengar notis ini dan bersetuju menyertai Wira Kitar." [Setuju] [Tidak setuju]. If not agreed: thank them, end, delete session data.
5. Optional details: "Nama anda? (boleh langkau)" and "No. kenderaan? (boleh langkau)" with [Langkau / Skip] buttons. Photo is NOT collected on WhatsApp; staff can take one at first visit with consent.
6. If this phone number already belongs to a collector (e.g. registered at the counter before), link to that collector instead of creating a new one, and record the consent.
7. Create (or update) the collector: collector_ref (e.g. WK-000042, never reused), phone (the WhatsApp number, verified by WhatsApp), name and plate if given, referring centre, language, onboarded_via = "whatsapp", status = active.
8. Record a collector_consent: collector_id, notice_version, language, each part acknowledged with timestamp, age_confirmed = true, consent = true, method = "whatsapp", message ids, created_at, salt (32 random hex characters). This record is immutable; withdrawal creates a new record.
9. Send the QR ID (Part 2) with a welcome message: their Wira Kitar ID, the QR image, and the commands they can use.

PART 2. WIRA KITAR QR ID
- QR payload: "WK1:" + collector_ref + ":" + issue_number + ":" + signature, where signature = first 16 hex characters of HMAC-SHA256(server secret, collector_ref + ":" + issue_number). No phone number or name in the QR.
- Generate the QR as a PNG (high error correction, with "WIRA KITAR" and the collector_ref printed under it, Wira Kitar colours #066839 and #1FA63D) and send it as a WhatsApp image.
- Printable card: from the collector's profile, staff can print a credit-card size card (QR, collector_ref, first name if given) for collectors without smartphones.
- Store qr_issue: collector_id, issue_number, issued_at, revoked_at, reason.
- Commands any registered collector can send:
  - KOD: resend current QR.
  - HILANG: revoke the current QR (issue_number + 1) and send a new one; old QR stops working immediately.
  - REKOD: reply with this month's total kg, number of visits and Wira Kitar fee share (no other people's data).
  - ADUAN: start a complaint (creates a grievance, category buttons, then free text or voice note).
  - BERHENTI: withdraw. Confirm with [Ya, berhenti] [Batal]. On confirm: status = withdrawn, revoke QR, create a consent record with consent = false, stop all messages. Past weighing records are kept anonymised as the notice says.
  - BANTUAN: list commands.

PART 3. SCAN AT THE CENTRE
- On the New Visit screen, add a large "Imbas QR" button as the first option (keep plate/phone search as backup).
- Scanning uses the device camera (inside the Android app and in Chrome on a laptop with a webcam). Use a reliable JS QR library.
- On scan: verify the prefix and signature, check the issue_number is current and the collector is active. If valid, show a confirmation card: collector_ref, first name, photo if any (otherwise initials), referring centre, last visit date, advance owed. Staff tap [Betul / Correct] to start the visit, or [Bukan orang ini / Not this person] which logs a possible misuse and returns to search.
- Invalid, revoked or withdrawn QR: clear red message in Bahasa Malaysia explaining what to do (e.g. "Kod lama. Minta pengumpul hantar KOD di WhatsApp").
- Record on each visit: identified_by = "qr" | "search" | "new", and qr_issue_number.
- First visit after WhatsApp onboarding: prompt staff to offer PPE and (optionally, with the collector's agreement) take a profile photo.

PART 4. ADMIN
- Notice manager: versions of the 5 parts per language, voice note per part, effective date. Publishing a new version means existing collectors are asked to re-consent: next time they message or are scanned, the app/bot walks them through the changed parts only, then asks for Setuju again.
- Onboarding funnel report: started, completed each part, age No, consent No, completed, by referring centre and by week. Average time to complete.
- Collector list shows onboarded_via, notice version consented, QR issue number, status.
- WhatsApp settings: provider key and number (server secrets), welcome text, poster generator per centre (A4 PDF with the wa.me QR and "Daftar Wira Kitar" instructions in BM and EN).

PART 5. QUESTIONS AND HUMAN HELP
- [Tanya] during onboarding and any message the bot doesn't understand creates a "Soalan" item for KLEAN staff with the conversation, shown in an admin inbox. Staff reply from the dashboard (sent as WhatsApp text within the 24-hour window).
- If a message mentions danger, violence, a child, or police, flag it as urgent in the inbox and notify admins immediately.

PART 6. BLOCKCHAIN ANCHORING (consent and ID records)
- Add consent records and QR issue/revocation events to the nightly anchoring export as new record types, alongside bags, with exactly these fields and no personal data:
  - record_type "consent": record_id, collector_ref, notice_version, language, consent (true/false), age_confirmed, method, parts_acknowledged (count), created_at (ISO UTC), salt.
  - record_type "qr_issue": record_id, collector_ref, issue_number, event ("issued" | "revoked"), created_at, salt.
- These records belong to the central collector registry, not a centre: list them in GET /api/anchoring/pending and export them under the centre code "WK" (e.g. /api/anchoring/export?centre=WK&date=2026-11-25), one batch per day.
- Same export and import endpoints and the same locking rule: once anchored, these records are read-only. I will update the anchoring job to accept these types.
- On the collector profile, show "Persetujuan direkod di blockchain" with the anchor date once anchored, and a Download proof button like bags.

DATA MODEL (add or adjust)
- onboarding_session: id, phone, language, step, parts_acknowledged, referring_centre, started_at, updated_at, completed_at, outcome (completed | under_18 | declined | expired).
- collector: add collector_ref (if not present, format WK-000000), onboarded_via, language, status (active | suspended | withdrawn), referring_centre.
- collector_consent: as above, immutable, plus anchor fields.
- qr_issue: id, collector_id, issue_number, issued_at, revoked_at, reason, plus anchor fields.
- notice_version: version, language, part_no, text, audio_file, effective_from.
- whatsapp_message_log: id, direction, phone_hash, collector_id, session_id, type, button_id, provider_message_id, created_at.
- visit: identified_by, qr_issue_number.

PRIVACY AND SAFETY
- Never put phone numbers, names or plates in the QR, the anchoring export or the blockchain.
- Phone numbers in logs are stored as a salted hash; the plain number is only on the collector record.
- Under-18 and declined sessions: delete personal data within 24 hours, keep only anonymous counts.
- Rate-limit the webhook and commands (e.g. max 20 messages per number per hour) to prevent abuse.

DONE WHEN
- A new person can send "DAFTAR C003" on WhatsApp, go through all 5 parts with buttons, confirm age and consent, and receive their QR ID image within 3 minutes.
- Staff scan the QR on the tablet and the visit starts with the right collector after one confirmation tap.
- HILANG revokes the old QR so it no longer scans, and sends a new one; BERHENTI withdraws the collector.
- The nightly anchoring export includes consent and qr_issue records with no personal data, and they show as anchored the next day.
- Under-18 and declined attempts leave no personal data.
