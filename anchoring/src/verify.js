#!/usr/bin/env node
// Check that one record belongs to an anchored batch.
//   node src/verify.js --batch anchors/C001-2026-11-24.json --record record.json
// record.json = the record exactly as exported from the dashboard (including its salt).
const fs = require('fs');
const M = require('./merkle');

const a = process.argv.slice(2);
const o = {};
for (let i = 0; i < a.length; i += 2) o[a[i].replace(/^--/, '')] = a[i + 1];
if (!o.batch || !o.record) { console.error('Usage: node src/verify.js --batch <batch.json> --record <record.json>'); process.exit(1); }

const batch = JSON.parse(fs.readFileSync(o.batch, 'utf8'));
const record = JSON.parse(fs.readFileSync(o.record, 'utf8'));
const leaf = M.leafHash(record);
const entry = batch.records.find(r => r.record_id === String(record.record_id));
if (!entry) { console.log('FAIL: record is not in this batch'); process.exit(2); }
if (entry.leaf_hash !== leaf.toString('hex')) { console.log('FAIL: record has been changed since it was anchored'); process.exit(2); }
const root = M.rootFromProof(leaf, entry.proof).toString('hex');
if (root !== batch.root) { console.log('FAIL: proof does not lead to the batch root'); process.exit(2); }
console.log('OK: record matches the anchored batch');
console.log('Root:', batch.root);
console.log('Anchor:', JSON.stringify(batch.anchor));
