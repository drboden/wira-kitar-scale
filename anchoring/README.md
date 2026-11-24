# Wira Kitar blockchain anchoring

Proves that Wira Kitar recycling records have not been changed since the day they were recorded, without putting any personal or price data on a blockchain.

```
Hercules dashboard (confirmed bags)  --daily export-->  anchor.js
   fingerprint every record (SHA-256, with a per-record salt)
   combine into one Merkle root
   write the root to Hedera (or Bitcoin via OpenTimestamps)
   write a proof for every record  --import-->  dashboard
Anyone with a record + its batch file  -->  verify.html  -->  "Record verified"
```

## What goes on the blockchain

Only this, once a day per centre:

```json
{ "v": 1, "scheme": "wira-kitar-merkle-v1", "batch_id": "C001-2026-11-16", "date": "2026-11-16", "count": 26, "root": "c1a6ad15..." }
```

No names, phone numbers, plates, prices or payouts. The per-record `salt` stops anyone guessing a record from its fingerprint.

## Record fields (fixed order)

`record_id, visit_id, centre_id, collector_ref, material, weight_kg, weight_source, scale_raw, scale_stable, captured_at, salt`

- `collector_ref` is an anonymised ID, not a name or plate.
- `weight_kg` is fingerprinted with 3 decimals.
- `salt` is 32 random hex characters, created when the bag is captured and never changed.
- Removed bags (`removed: true`) are not anchored.

Changing any field, even by 0.001 kg, breaks the proof.

## Set up

Needs Node.js 18 or newer.

```bash
cd anchoring
npm install
```

## Use

```bash
# Test run, no blockchain
node src/anchor.js --input samples/records-2026-11-16.json --date 2026-11-16 --method none

# Free: Bitcoin via OpenTimestamps (confirms in a few hours)
node src/anchor.js --input export.json --date 2026-11-24 --method ots
node src/upgrade.js anchors/C001-2026-11-24.ots      # a few hours later

# Hedera (recommended once the account is set up)
node src/anchor.js --input export.json --date 2026-11-24 --method hedera

# Check one record from the command line
node src/verify.js --batch anchors/C001-2026-11-24.json --record record.json
```

Input can be the dashboard's JSON export or a CSV export with the field names above as headers.

Outputs in `anchors/`:
- `<batch>.json`: the batch, its anchor receipt and a proof for every record. Keep it and give it to auditors.
- `<batch>-proofs.csv`: import into the dashboard so each bag stores its `leaf_hash`, `batch_id` and `proof`.
- `<batch>.ots`: the OpenTimestamps proof (OTS method only).

## Hedera set-up (one-off)

1. Create a Hedera account at [portal.hedera.com](https://portal.hedera.com). Start on **testnet** (free test HBAR).
2. Put the details in environment variables (in a secret store, never in code or the repo):
   ```
   HEDERA_NETWORK=testnet
   HEDERA_ACCOUNT_ID=0.0.xxxxxxx
   HEDERA_PRIVATE_KEY=<private key>
   ```
3. Create KLEAN's anchoring topic. Only KLEAN's key can post to it; anyone can read it.
   ```bash
   node src/hedera-create-topic.js
   ```
   Then set `HEDERA_TOPIC_ID` to the ID it prints.
4. For live use, repeat on **mainnet** with a funded account. Each anchor costs about US$0.0001.

## Running it daily

Hercules may not run scheduled server jobs. Options:
- A small scheduled cloud function (Google Cloud Run or similar) that pulls the day's confirmed bags from the dashboard's export API, runs `anchor.js`, and posts the proofs back.
- The GitHub Actions workflow in this repo, on a daily schedule, with the Hedera keys stored as repository secrets.
- At the start, run it by hand once a day.

## Verify page

`verify.html` is a single page that runs entirely in the browser. Paste a record and load its batch file. It recomputes the fingerprint, follows the proof to the root and, for Hedera batches, reads the root from Hedera's public mirror node to confirm it was published on that date.
