#!/usr/bin/env node
// Nightly anchoring: ask the dashboard what is pending, export each centre-day,
// anchor it on Hedera, and import the result back. Safe to re-run: the dashboard
// only lists days that still have unanchored bags.
//
// Env: HERCULES_BASE_URL, HERCULES_API_KEY, plus the Hedera variables (see hedera.js)
// Usage: node src/nightly.js [resultsDir] [--dry-run]
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const args = process.argv.slice(2);
const dryRun = args.includes('--dry-run');
const resultsDir = args.find(a => !a.startsWith('--')) || 'out';
const cfg = JSON.parse(fs.readFileSync('hedera-config.json', 'utf8'));
const base = (process.env.HERCULES_BASE_URL || cfg.hercules_base_url || '').replace(/\/+$/, '');
const key = process.env.HERCULES_API_KEY;
const maxBatches = Number(process.env.MAX_BATCHES || 50);
process.env.HEDERA_NETWORK = process.env.HEDERA_NETWORK || cfg.network;
process.env.HEDERA_ACCOUNT_ID = process.env.HEDERA_ACCOUNT_ID || cfg.account_id;
process.env.HEDERA_KEY_TYPE = process.env.HEDERA_KEY_TYPE || cfg.key_type || 'ECDSA';
process.env.HEDERA_TOPIC_ID = process.env.HEDERA_TOPIC_ID || cfg.topic_id;

if (!base) { console.log('Not set up yet: add hercules_base_url to hedera-config.json. Nothing to do.'); process.exit(0); }
if (!key) throw new Error('Set the HERCULES_API_KEY secret');

async function api(method, p, body) {
  const res = await fetch(base + p, {
    method,
    headers: { Authorization: `Bearer ${key}`, 'Content-Type': 'application/json', Accept: 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let json;
  try { json = JSON.parse(text); } catch { json = { raw: text.slice(0, 500) }; }
  if (!res.ok) throw new Error(`${method} ${p} -> ${res.status}: ${JSON.stringify(json).slice(0, 800)}`);
  return json;
}

(async () => {
  const runDir = path.join(resultsDir, 'nightly');
  const exportDir = path.join(resultsDir, 'exports');
  const anchorDir = path.join(resultsDir, 'anchors');
  for (const d of [runDir, exportDir, anchorDir]) fs.mkdirSync(d, { recursive: true });

  const log = { started_at: new Date().toISOString(), dry_run: dryRun, network: process.env.HEDERA_NETWORK, batches: [] };
  let failed = 0;
  try {
    const pending = await api('GET', '/api/anchoring/pending');
    const list = Array.isArray(pending) ? pending : pending.pending || pending.items || [];
    console.log(`Pending centre-days: ${list.length}`);
    for (const item of list.slice(0, maxBatches)) {
      const { centre_id, date } = item;
      const entry = { centre_id, date, expected: item.count };
      try {
        const recs = await api('GET', `/api/anchoring/export?centre=${encodeURIComponent(centre_id)}&date=${encodeURIComponent(date)}`);
        const records = Array.isArray(recs) ? recs : recs.records || [];
        entry.exported = records.length;
        if (!records.length) { entry.result = 'nothing to anchor'; log.batches.push(entry); continue; }
        const exportFile = path.join(exportDir, `${centre_id}-${date}.json`);
        fs.writeFileSync(exportFile, JSON.stringify(records, null, 2));

        const out = execFileSync('node', ['src/anchor.js', '--input', exportFile, '--date', date,
          '--method', dryRun ? 'none' : (process.env.ANCHOR_METHOD || 'hedera'), '--out', anchorDir], { encoding: 'utf8', env: process.env, timeout: 120000 });
        const batchId = (out.match(/Batch:\s+(\S+)/) || [])[1];
        entry.batch_id = batchId;
        entry.root = (out.match(/Root:\s+(\S+)/) || [])[1];
        const batch = JSON.parse(fs.readFileSync(path.join(anchorDir, `${batchId}.json`), 'utf8'));
        entry.anchor = batch.anchor;

        if (dryRun) { entry.result = 'dry run: anchored locally only, not imported'; }
        else {
          entry.import = await api('POST', '/api/anchoring/import', batch);
          entry.result = 'anchored and imported';
        }
        console.log(`${centre_id} ${date}: ${entry.exported} records -> ${entry.result}`);
      } catch (e) {
        failed++;
        entry.result = 'FAILED';
        entry.error = String(e.stderr || e.message).slice(0, 2000);
        console.log(`${centre_id} ${date}: FAILED ${entry.error}`);
      }
      log.batches.push(entry);
    }
  } catch (e) {
    failed++;
    log.error = e.message;
    console.log('FAILED:', e.message);
  }
  log.finished_at = new Date().toISOString();
  log.failed = failed;
  const f = path.join(runDir, `${log.started_at.replace(/[:.]/g, '-')}.json`);
  fs.writeFileSync(f, JSON.stringify(log, null, 2));
  console.log(`Log: ${f}`);
  process.exit(failed ? 1 : 0);
})();
