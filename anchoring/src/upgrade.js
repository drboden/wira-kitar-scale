#!/usr/bin/env node
// Complete an OpenTimestamps proof once Bitcoin has confirmed it (a few hours after stamping).
//   node src/upgrade.js anchors/C001-2026-11-24.ots
const fs = require('fs');
const OTS = require('opentimestamps');

(async () => {
  const file = process.argv[2];
  if (!file) throw new Error('Usage: node src/upgrade.js <file.ots>');
  const d = OTS.DetachedTimestampFile.deserialize(fs.readFileSync(file));
  const changed = await OTS.upgrade(d);
  if (changed) {
    fs.writeFileSync(file, Buffer.from(d.serializeToBytes()));
    console.log('Upgraded: the proof now includes the Bitcoin attestation.');
  } else {
    console.log('Not confirmed yet. Try again in an hour or two.');
  }
  const batchFile = file.replace(/\.ots$/, '.json');
  if (changed && fs.existsSync(batchFile)) {
    const b = JSON.parse(fs.readFileSync(batchFile, 'utf8'));
    b.anchor.status = 'confirmed';
    b.anchor.confirmed_checked_at = new Date().toISOString();
    fs.writeFileSync(batchFile, JSON.stringify(b, null, 2));
  }
})().catch(e => { console.error('Error:', e.message); process.exit(1); });
