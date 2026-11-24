CONTEXT
We are Klean (Janz Technologies Sdn Bhd). The Wira Kitar Recycler Dashboard records every bag that collection centres buy from informal waste collectors, with the weight taken straight from the scale. Brand owners use these records for their EPR reports, and we will issue KleanX plastic credits from them. To prove the records are never changed after the fact, a separate KLEAN job fingerprints each day's confirmed bags and anchors one combined fingerprint on a public blockchain (Hedera, or Bitcoin via OpenTimestamps). The dashboard stays the system of record. It needs to store a few extra fields, export the day's records in a fixed format, accept the proofs back, and show the anchoring status.

The blockchain job is already built (outside Hercules). Do not add any blockchain libraries or keys to the dashboard.

1. NEW FIELDS ON visit_bag
- salt: text, 32 random hex characters (16 random bytes). Generate it once when the bag is captured, using a cryptographically secure random source. Never change it, never show it to centre staff.
- collector_ref: text, an anonymised collector ID such as "COL-0001". Add it to the collector table (unique, assigned on registration, never reused) and copy it onto each bag. It must not contain the name, phone or plate.
- leaf_hash: text, nullable. Filled when the proof is imported.
- anchor_batch_id: text, nullable.
- anchor_proof: JSON, nullable.
- anchored_at: timestamp, nullable.

2. NEW TABLE anchor_batch
- batch_id (text, unique, e.g. "C001-2026-11-24"), centre_id, date, record_count, root (64-character hex), method ('hedera' or 'opentimestamps'), network, topic_id, sequence_number, transaction_id, consensus_timestamp, ots_file (file upload), status ('pending', 'anchored', 'confirmed'), explorer_url, batch_file (file upload: the full batch JSON), created_at.

3. LOCKING
- Once a bag has an anchor_batch_id, it is permanently read-only, including for admins. A correction must be a new adjusting record with a reason, linked to the original, and it is anchored in the next batch.
- Bags in a visit that is not yet confirmed, and removed bags, are never anchored.

4. DAILY EXPORT (admin only)
- Screen "Blockchain anchoring" under Klean admin. Pick a centre and a date. Show how many confirmed, unanchored bags there are.
- Button "Export for anchoring" downloads a JSON file: an array with one object per confirmed, not removed, not yet anchored bag, with exactly these keys and types:
  - record_id (string, the visit_bag id)
  - visit_id (string)
  - centre_id (string, the centre's code, e.g. "C001")
  - collector_ref (string)
  - material (string, the material's Malay name in lower case, e.g. "plastik")
  - weight_kg (number)
  - weight_source ("scale" or "manual")
  - scale_raw (string, the exact indicator line; empty string for manual)
  - scale_stable (boolean)
  - captured_at (string, ISO 8601 in UTC, e.g. "2026-11-24T03:15:20.000Z")
  - salt (string)
  - Corrections only: adjusts_record_id (string, the original bag's record_id) and reason_code (string, e.g. "wrong_weight", "wrong_material", "duplicate"). Leave both keys out completely on normal bags. A correction's weight_kg is the corrected full weight, not the difference, and it has its own new record_id and salt.
- Export values exactly as stored. Do not round, trim or reformat them, because any change breaks the fingerprint.
- Also provide the same export as an authenticated API endpoint (admin API key), GET /api/anchoring/export?centre=C001&date=2026-11-24, so the job can run automatically later.

5. IMPORT PROOFS (admin only)
- On the same screen, "Import anchor results": upload the batch JSON file (C001-2026-11-24.json) and, for OpenTimestamps, the .ots file.
- Create the anchor_batch row from the file: batch_id, date, count, root, and the anchor fields (method, network, topic_id, sequence_number, transaction_id, consensus_timestamp, status, explorer, ots_file).
- For every entry in the file's "records" array, find the bag by record_id and set leaf_hash, anchor_batch_id = batch_id, anchor_proof = the entry's "proof" array, and anchored_at = now.
- Refuse the import if any record_id is unknown or already anchored, or if record_count doesn't match. Show which records caused it.
- Also offer POST /api/anchoring/import with the same JSON body, admin API key only.

6. SHOWING IT
- On each visit and bag, show a small badge: grey "Not yet anchored", green "Anchored" (with date), or "Confirmed on Bitcoin" for OpenTimestamps batches marked confirmed. Clicking it opens the batch details and the explorer link.
- Button "Download proof" on a bag. It downloads a zip with:
  - record.json: that bag's export object, exactly as in section 4;
  - batch.json: the anchor_batch's batch_file;
  - the .ots file, if any.
  An auditor can then load these into our verify page.
- Producer EPR reports and KleanX credit reports: add a column "Anchored kg" and the batch IDs behind each total. By default, credits can only be issued from anchored, scale-captured kg.
- Admin overview: per centre and day, record count, anchored or not, method, and a link to each batch. Highlight any day older than 2 days that hasn't been anchored.

7. PRIVACY
- Never put names, phone numbers, plates, prices or payouts into the export, the batch files or anything sent to the blockchain job. Only the fields listed in section 4.
- salt, leaf_hash and anchor_proof are visible to Klean admin only.

DONE WHEN
- Exporting a centre's day, running our anchoring job, and importing the result marks every bag in that day as anchored, and those bags can no longer be edited.
- "Download proof" for any anchored bag gives files that our verify page reports as "Record verified".
- Editing a value on a bag before anchoring changes nothing else; after anchoring it is impossible.

Keep all existing features as they are.
