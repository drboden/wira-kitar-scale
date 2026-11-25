CONTEXT
We are Klean (Janz Technologies Sdn Bhd). The Wira Kitar Recycler Dashboard records what collection centres buy from informal waste collectors. Collectors will now onboard themselves on WhatsApp. This REPLACES the earlier "WhatsApp onboarding" prompt that used 360dialog: we now use respond.io, and the conversation itself (language, notice, age check, consent) is already built as a respond.io workflow. Your job is everything after consent: create or link the collector, record consent, issue the Wira Kitar QR ID, send it back through respond.io, handle collector commands, scan the QR at the centre, and add the new records to the nightly blockchain anchoring export.

Build this in stages, pausing after each part so I can test. Keep everything already built (scale capture, visits and bags, advances, cash float, anchoring, corrections, reports, ISO 59014 work) working exactly as it is. Bahasa Malaysia first with an English toggle; short messages for collectors with limited literacy.

WHAT RESPOND.IO ALREADY DOES (do not rebuild)
Workflow "Wira Kitar Onboarding" (trigger: conversation opened):
1. Language buttons [Bahasa Malaysia] [English] -> contact field wk_language.
2. Collector notice v1.0 in 5 parts, each with a [Faham] / [Understood] button. All 5 must be pressed to continue.
3. Age: [Ya]/[Tidak] or [Yes]/[No] -> contact field wk_age_18_plus. Under 18: polite message, flow ends.
4. Consent: [Setuju]/[Tidak setuju] or [Agree]/[Don't agree] -> contact field wk_consent. Declined: polite message, flow ends.
5. On consent: sets wk_notice_version = "1.0", adds the tag wk_consented, and tells the collector their QR ID card is coming.
Other contact fields that exist for you to write back: wk_collector_id, wk_referring_centre, wk_plate.

The trigger for you is the tag wk_consented being added. Never decide consent from the button wording, because it differs by language.

SECRETS (server-side only, never in the client or logs)
- RESPONDIO_API_TOKEN: respond.io Developer API access token (I will create it in respond.io Settings > Integrations > Developer API).
- RESPONDIO_WEBHOOK_KEY: the signing key respond.io shows when I create each webhook.
- QR_SIGNING_SECRET: random 32+ byte secret for QR signatures.
- PHONE_HASH_SALT: random salt for hashing phone numbers in logs.

PART 1. RESPOND.IO WEBHOOK RECEIVER
1. Add POST /api/respondio/webhook (Convex HTTP action on https://ideal-bulldog-102.convex.site). Give me the full URL so I can register it in respond.io (Settings > Integrations > Webhooks).
2. Verify the request signature using RESPONDIO_WEBHOOK_KEY exactly as described in respond.io's webhook documentation; reject anything unsigned or invalid with 401. Respond 200 within a few seconds and do the work asynchronously (Convex scheduler), because respond.io retries slow or failed deliveries. Make processing idempotent on the event id so retries never create duplicates.
3. Events to handle (I will subscribe the webhook to these):
   - Contact tag updated: when wk_consented is added -> run Part 2.
   - Contact updated (field change): if wk_consent becomes "Tidak setuju" or "Don't agree", or wk_age_18_plus becomes "Tidak" or "No", record only an anonymous count (date, outcome, language). Store no phone, name or contact id.
   - Incoming message: (a) if the very first message from a contact is "DAFTAR <centre code>" (from the centre poster's wa.me link), store the centre code against the contact id and write it to wk_referring_centre; (b) if the contact is a registered collector, handle commands (Part 4); otherwise ignore.
4. Log every webhook and every outbound API call in whatsapp_message_log: id, direction, event type, respondio_contact_id, phone_hash (SHA-256 of phone + PHONE_HASH_SALT), collector_id, button/command, provider message id, created_at. Do not log message text beyond commands and the fields we collect.
5. Rate limit: at most 20 inbound events per contact per hour; drop and log the excess.

PART 2. CREATE OR LINK THE COLLECTOR, RECORD CONSENT
When wk_consented is added:
1. Fetch the contact from the respond.io Developer API (GET contact by id) to read: phone, wk_language, wk_age_18_plus, wk_consent, wk_notice_version, wk_referring_centre.
2. Double-check: wk_age_18_plus is "Ya" or "Yes" AND wk_consent is "Setuju" or "Agree". If not, do nothing and flag it in the admin inbox.
3. If a collector with this phone already exists (e.g. registered at the counter), link to that collector. Otherwise create one.
4. Collector ID: use the EXISTING anonymous format COL-0001, COL-0002, ... from the same sequence the dashboard already uses. Never reuse a number, even after withdrawal. Existing collectors keep their current COL id (it is already in anchored blockchain records and must not change).
5. Collector record: collector_ref (COL-xxxx), phone, referring_centre, language ("ms" for Bahasa Malaysia, "en" for English), onboarded_via = "whatsapp", respondio_contact_id, status = active.
6. Record an immutable collector_consent: collector_id, notice_version (from wk_notice_version), language, consent = true, age_confirmed = true, method = "whatsapp", parts_acknowledged = 5, respondio_contact_id, created_at (ISO UTC), salt (32 random hex characters). Withdrawal later creates a new record with consent = false; never edit this one.
7. Write back to respond.io: wk_collector_id = COL-xxxx (update contact custom field via the API).
8. Issue QR issue_number 1 (or the next number if the collector already had one) and send it (Part 3).

PART 3. WIRA KITAR QR ID AND SENDING IT
1. QR payload: "WK1:" + collector_ref + ":" + issue_number + ":" + sig, where sig = first 16 hex characters of HMAC-SHA256(QR_SIGNING_SECRET, collector_ref + ":" + issue_number). Example: WK1:COL-0042:1:3f9a0c1b7d2e4a65. No phone, name or plate in the QR.
2. Generate a PNG (high error correction, "WIRA KITAR" and the collector_ref printed under it, colours #066839 and #1FA63D).
3. respond.io needs a public URL to send an image. Serve it from GET /api/qr/<token>.png where token is a random 32-character value stored with the qr_issue, valid for 7 days. It returns only the image, with no-store caching. Never put the collector_ref or phone in the URL.
4. Send through the respond.io Developer API "send message" endpoint to the contact: first the image (attachment type image, the URL above), then a text in the contact's language:
   BM: "Ini ID Wira Kitar anda: COL-0042. Tunjukkan kod QR ini di pusat kitar semula setiap kali anda menimbang. Hantar KOD untuk dapatkan semula kod, HILANG jika telefon hilang, REKOD untuk rekod bulan ini, ADUAN untuk membuat aduan, BERHENTI untuk berhenti, BANTUAN untuk senarai arahan."
   EN: "Your Wira Kitar ID is COL-0042. Show this QR code at the recycling centre every time you weigh. Send KOD to get your code again, HILANG if you lose your phone, REKOD for this month's record, ADUAN to make a complaint, BERHENTI to leave, BANTUAN for the list of commands."
   Messages go out within respond.io's 24-hour WhatsApp window (the collector has just messaged us). If the send fails, retry 3 times with backoff and then flag it in the admin inbox.
5. Printable card from the collector profile: credit-card size, QR + collector_ref + first name if given, for collectors without smartphones.
6. Store qr_issue: collector_id, issue_number, issued_at, revoked_at, reason, image_token, plus anchor fields.

PART 4. COLLECTOR COMMANDS (incoming messages from registered collectors)
Match case-insensitively on the first word; reply in the collector's language via the respond.io API.
- KOD: resend the current QR (new image token).
- HILANG: revoke the current QR (revoked_at, reason "lost"), issue issue_number + 1, send the new one. The old QR stops scanning immediately.
- REKOD: this month's total kg, number of visits and Wira Kitar fee share for this collector only.
- ADUAN: create a grievance (same grievance module as the ISO 59014 work), reply with category buttons, then accept free text or a voice note.
- BERHENTI: ask to confirm with [Ya, berhenti] [Batal]. On confirm: status = withdrawn, revoke QR, new collector_consent with consent = false, remove the wk_consented tag in respond.io, send a short goodbye, stop all messages. Past weighing records are kept anonymised as the notice says.
- BANTUAN: list the commands.
- Anything else: create a "Soalan" item in the admin inbox (staff reply from the dashboard, sent through respond.io within the 24-hour window). Flag as urgent and notify admins at once if it mentions danger, violence, a child or police.

Note: respond.io may also route these messages to its own inbox. That is fine; the dashboard is the system of record.

PART 5. SCAN AT THE CENTRE
1. On New Visit, add a large "Imbas QR" button first (keep plate/phone search as backup). Use the device camera (Android app and Chrome on a laptop webcam) with a reliable JS QR library.
2. On scan: check the prefix, recompute the signature, check issue_number is current and the collector is active. If valid, show a confirmation card: collector_ref, first name, photo if any (else initials), referring centre, last visit date, advance owed. [Betul / Correct] starts the visit; [Bukan orang ini / Not this person] logs possible misuse and returns to search.
3. Invalid, revoked or withdrawn: clear red message in BM, e.g. "Kod lama. Minta pengumpul hantar KOD di WhatsApp."
4. Store on each visit: identified_by = "qr" | "search" | "new", qr_issue_number.
5. First visit after WhatsApp onboarding: prompt staff to offer PPE and, with the collector's agreement, take a profile photo.

PART 6. BLOCKCHAIN ANCHORING (consent and QR records)
Add these to the nightly anchoring export as new record types, alongside bags, with exactly these fields and no personal data (the anchoring job already accepts them):
- record_type "consent": record_type, record_id, collector_ref, notice_version, language, consent (true/false), age_confirmed (true/false), method, parts_acknowledged (number), created_at (ISO UTC), salt.
- record_type "qr_issue": record_type, record_id, collector_ref, issue_number, event ("issued" | "revoked"), created_at (ISO UTC), salt.
These belong to the central collector registry, not a centre: list them in GET /api/anchoring/pending and export them under the centre code "WK" (e.g. /api/anchoring/export?centre=WK&date=2026-11-25), one batch per day. Same import endpoint and locking rule: once anchored they are read-only. On the collector profile show "Persetujuan direkod di blockchain" with the anchor date and a Download proof button, as for bags.

PART 7. ADMIN
- Collector list: onboarded_via, notice version consented, QR issue number, status, respond.io link.
- Onboarding funnel by week and referring centre: consented (from Part 2), declined and under-18 (anonymous counts from Part 1).
- Poster generator per centre: A4 PDF with a wa.me QR that pre-fills "DAFTAR <centre code>" to our WhatsApp number, with instructions in BM and EN. Number configurable in settings.
- Notice versions: when I publish notice v1.1 later, existing collectors must re-consent. For now just store notice_version on every consent so we can find who needs it.

DATA MODEL (add or adjust)
- collector: collector_ref (COL-xxxx, existing sequence), onboarded_via, language, status (active | suspended | withdrawn), referring_centre, respondio_contact_id.
- collector_consent: as Part 2, immutable, plus anchor fields.
- qr_issue: as Part 3, plus anchor fields.
- onboarding_outcome_count: date, outcome (declined | under_18), language, referring_centre. No personal data.
- respondio_contact_centre: respondio_contact_id, centre_code, first_seen_at (for DAFTAR codes before consent; delete after 7 days if no consent).
- whatsapp_message_log: as Part 1.
- visit: identified_by, qr_issue_number.

PRIVACY AND SAFETY
- Never put phone numbers, names or plates in the QR, the image URL, the anchoring export or the blockchain.
- Declined and under-18 contacts: store nothing except anonymous counts.
- All respond.io calls server-side only.

DONE WHEN
- I message our WhatsApp number "DAFTAR C003", go through the respond.io flow, tap Setuju, and within 1 minute receive my QR image and the welcome text with ID COL-xxxx; respond.io shows wk_collector_id and wk_referring_centre = C003.
- An existing counter-registered collector who does the same keeps their old COL id.
- Staff scan the QR on the tablet and the visit starts after one confirmation tap.
- HILANG makes the old QR fail and sends a new one; BERHENTI withdraws and removes the tag.
- Declined and under-18 attempts leave no personal data in the dashboard.
- The next nightly anchoring run includes the consent and qr_issue records under "WK" and they show as anchored the next day.
